package com.echeo.service;

import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.entity.Group;
import com.echeo.model.entity.GroupEvent;
import com.echeo.model.entity.GroupMember;
import com.echeo.model.entity.PaymentToken;
import com.echeo.model.enums.PaymentStatus;
import com.echeo.repository.EventMemberStatusRepository;
import com.echeo.repository.GroupEventRepository;
import com.echeo.util.PaymentRules;
import com.echeo.util.ReminderPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tâche planifiée de relance des paiements de groupe (toutes les minutes, fuseau Africa/Douala).
 *
 * Règles (voir util.ReminderPlan) :
 *  - AVANT l'échéance : uniquement aux jours choisis par l'organisateur (facultatif).
 *  - APRÈS l'échéance : seulement si la date limite est passée ET que le membre n'a rien signalé
 *    (ni paiement, ni clic sur le lien public). Argent : calendrier fixe jusqu'au paiement ;
 *    sans argent : une seule relance le lendemain.
 *  - Une seule relance par membre et par jour. Si une occurrence suivante a pris le relais
 *    (récurrence), les relances de l'ancienne occurrence s'arrêtent : la dette a été reportée.
 *  - L'envoi passe par NotificationDispatchService : un e-mail qui échoue est renvoyé tout seul
 *    plus tard, sans redéclencher de relance (l'étape est considérée comme traitée).
 */
@Service
public class NotificationSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchedulerService.class);
    private static final LocalTime DEFAULT_TIME = LocalTime.of(8, 0);
    private static final java.time.ZoneId ZONE = java.time.ZoneId.of("Africa/Douala");

    private static final List<PaymentStatus> RELANCE_STATUSES = List.of(
            PaymentStatus.PENDING, PaymentStatus.PARTIALLY_PAID, PaymentStatus.OVERDUE
    );
    // Sans argent : tant que le membre n'a pas cliqué (SEEN) on peut relancer.
    private static final List<PaymentStatus> RELANCE_STATUSES_SANS_ARGENT = List.of(
            PaymentStatus.NOT_SEEN, PaymentStatus.OVERDUE
    );

    private static final Duration PAYMENT_LINK_VALIDITY = Duration.ofDays(10);

    private final GroupEventRepository groupEventRepository;
    private final EventMemberStatusRepository eventMemberStatusRepository;
    private final NotificationDispatchService dispatchService;
    private final PaymentService paymentService;

    @Value("${echeo.frontend.base-url:https://echeo-one.vercel.app}")
    private String frontendBaseUrl;

    public NotificationSchedulerService(GroupEventRepository groupEventRepository,
                                         EventMemberStatusRepository eventMemberStatusRepository,
                                         NotificationDispatchService dispatchService,
                                         PaymentService paymentService) {
        this.groupEventRepository = groupEventRepository;
        this.eventMemberStatusRepository = eventMemberStatusRepository;
        this.dispatchService = dispatchService;
        this.paymentService = paymentService;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void runGroupPaymentReminders() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime now = LocalDateTime.now(ZONE);

        // Fenêtre utile : jusqu'à 60 jours avant, 56 jours après.
        List<GroupEvent> events = groupEventRepository.findByEventDateBetween(today.minusDays(56), today.plusDays(60));
        for (GroupEvent event : events) {
            long delta = java.time.temporal.ChronoUnit.DAYS.between(today, event.getEventDate());
            boolean hasMoney = event.hasMoney();
            String label;
            if (delta >= 0) {
                if (!ReminderPlan.shouldRemindBefore(event.getReminderDaysBefore(), delta)) {
                    continue;
                }
                label = delta == 0 ? "J0" : "J-" + delta;
            } else {
                long late = -delta;
                if (!ReminderPlan.shouldRemindOverdue(hasMoney, late)) {
                    continue;
                }
                // Relève prise par l'occurrence suivante : la dette est reportée, on ne relance plus ici.
                if (groupEventRepository.existsByPreviousEventIdAndEventDateLessThanEqual(event.getId(), today)) {
                    continue;
                }
                label = "J+" + late + " (en retard)";
            }

            LocalTime effectiveTime = event.getEventTime() != null ? event.getEventTime() : DEFAULT_TIME;
            if (now.isBefore(today.atTime(effectiveTime))) {
                continue;
            }

            List<EventMemberStatus> toRemind = eventMemberStatusRepository.findByEvent_IdAndStatusIn(
                    event.getId(), hasMoney ? RELANCE_STATUSES : RELANCE_STATUSES_SANS_ARGENT);

            for (EventMemberStatus status : toRemind) {
                if (alreadySentAnyToday(status)) {
                    continue;
                }
                // Occurrence reportée pas encore échue : si le membre doit encore l'occurrence
                // précédente, il reçoit déjà ses relances de retard -> pas de doublon.
                if (delta >= 0 && !event.isCarryApplied() && event.getPreviousEventId() != null
                        && owesPrevious(event, status)) {
                    continue;
                }
                if (hasMoney && PaymentRules.remainingCash(status.getPaidAmount(), status.getRequiredAmount()).signum() <= 0) {
                    continue;
                }
                if (hasMoney) {
                    sendPaymentReminder(event, status, label);
                } else {
                    sendAcknowledgmentReminder(event, status, label);
                }
                status.setLastReminderStage(label);
                status.setLastReminderSentAt(OffsetDateTime.now(ZONE));
                eventMemberStatusRepository.save(status);
                log.info("Relance traitée: event={}, memberStatus={}, {}", event.getId(), status.getId(), label);
            }
        }
    }

    private boolean owesPrevious(GroupEvent event, EventMemberStatus status) {
        return eventMemberStatusRepository
                .findFirstByEvent_IdAndGroupMember_Id(event.getPreviousEventId(), status.getGroupMember().getId())
                .map(prev -> PaymentRules.remainingCash(prev.getPaidAmount(), prev.getRequiredAmount()).signum() > 0)
                .orElse(false);
    }

    private boolean alreadySentAnyToday(EventMemberStatus status) {
        OffsetDateTime last = status.getLastReminderSentAt();
        return last != null && last.atZoneSameInstant(ZONE).toLocalDate().isEqual(LocalDate.now(ZONE));
    }

    private String frontendBase() {
        String base = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "https://echeo-one.vercel.app" : frontendBaseUrl;
        return base.replaceAll("/+$", "");
    }

    private void sendPaymentReminder(GroupEvent event, EventMemberStatus status, String label) {
        GroupMember member = status.getGroupMember();
        Group group = event.getGroup();
        BigDecimal fee = event.getWithdrawalFeeAmount();
        BigDecimal required = PaymentRules.nz(status.getRequiredAmount());
        // Montant à envoyer, frais de retrait inclus.
        BigDecimal remaining = PaymentRules.remainingToPay(status.getPaidAmount(), required, fee);
        BigDecimal total = PaymentRules.amountDue(required, fee);

        String paymentLink;
        try {
            PaymentToken token = paymentService.generatePaymentToken(status.getId(), PAYMENT_LINK_VALIDITY);
            paymentLink = frontendBase() + "/pay/" + token.getTokenUuid();
        } catch (Exception ex) {
            log.warn("Échec de la génération du lien de paiement pour le statut {} : {}", status.getId(), ex.getMessage());
            paymentLink = null;
        }

        String locale = NotificationDispatchService.resolveLocale(member, group);
        boolean en = locale.equals("en");
        String body = buildReminderMessage(member.getContactFullName(), remaining, total, event.getTitle(),
                label, paymentLink, en, fee != null && fee.signum() > 0);
        dispatchService.sendEmail(resolveRecipientContact(member),
                en ? "ÉCHÉO — Payment reminder" : "ÉCHÉO — Rappel de paiement", body);

        if (group.isNotifyOwnerOnReminders() && group.getOwner() != null) {
            boolean ownerEn = NotificationDispatchService.ownerLocale(group).equals("en");
            String ownerMessage = ownerEn
                    ? String.format("Reminder sent to %s (%s) for '%s': %s FCFA left to pay (fees included) (due %s).",
                        member.getContactFullName(), resolveRecipientContact(member), event.getTitle(),
                        formatAmount(remaining), label)
                    : String.format("Relance envoyée à %s (%s) pour l'événement '%s' : reste %s FCFA à payer (frais inclus) (échéance %s).",
                        member.getContactFullName(), resolveRecipientContact(member), event.getTitle(),
                        formatAmount(remaining), label);
            dispatchService.sendEmail(group.getOwner().getEmail(),
                    ownerEn ? "ÉCHÉO — Reminder sent" : "ÉCHÉO — Relance envoyée", ownerMessage);
        }
    }

    private void sendAcknowledgmentReminder(GroupEvent event, EventMemberStatus status, String label) {
        GroupMember member = status.getGroupMember();
        Group group = event.getGroup();
        String ackLink = status.getPublicAckToken() != null
                ? frontendBase() + "/ack/" + status.getPublicAckToken() : null;

        String locale = NotificationDispatchService.resolveLocale(member, group);
        boolean en = locale.equals("en");
        StringBuilder message = new StringBuilder();
        if (en) {
            message.append("Hello ").append(member.getContactFullName()).append(", this is a reminder: \"")
                    .append(event.getTitle()).append("\"");
            if (event.getDescription() != null && !event.getDescription().isBlank()) {
                message.append(" — ").append(event.getDescription());
            }
            message.append(" (due ").append(label).append(").");
            if (ackLink != null) {
                message.append(" Click here to confirm you have seen this message: ").append(ackLink);
            }
        } else {
            message.append("Bonjour ").append(member.getContactFullName()).append(", ceci est un rappel : \"")
                    .append(event.getTitle()).append("\"");
            if (event.getDescription() != null && !event.getDescription().isBlank()) {
                message.append(" — ").append(event.getDescription());
            }
            message.append(" (échéance ").append(label).append(").");
            if (ackLink != null) {
                message.append(" Clique ici pour confirmer que tu as bien vu ce message : ").append(ackLink);
            }
        }
        dispatchService.sendEmail(resolveRecipientContact(member),
                en ? "ÉCHÉO — Reminder" : "ÉCHÉO — Rappel", message.toString());

        if (group.isNotifyOwnerOnReminders() && group.getOwner() != null) {
            boolean ownerEn = NotificationDispatchService.ownerLocale(group).equals("en");
            String ownerMessage = ownerEn
                    ? String.format("Reminder sent to %s (%s) for '%s' (due %s, not seen yet).",
                        member.getContactFullName(), resolveRecipientContact(member), event.getTitle(), label)
                    : String.format("Rappel envoyé à %s (%s) pour l'événement '%s' (échéance %s, pas encore vu).",
                        member.getContactFullName(), resolveRecipientContact(member), event.getTitle(), label);
            dispatchService.sendEmail(group.getOwner().getEmail(),
                    ownerEn ? "ÉCHÉO — Reminder sent" : "ÉCHÉO — Rappel envoyé", ownerMessage);
        }
    }

    private String buildReminderMessage(String fullName, BigDecimal remaining, BigDecimal total,
                                         String eventTitle, String label, String paymentLink,
                                         boolean en, boolean withFee) {
        StringBuilder message = new StringBuilder();
        if (en) {
            message.append(String.format(
                    "Hello %s, you still have %s FCFA to pay for '%s' (due %s)%s.",
                    fullName, formatAmount(remaining), eventTitle, label,
                    withFee ? ", withdrawal fees included" : ""));
            if (paymentLink != null) {
                message.append(" Pay here: ").append(paymentLink);
            }
        } else {
            message.append(String.format(
                    "Bonjour %s, il vous reste %s FCFA à payer pour l'événement '%s' (échéance %s)%s.",
                    fullName, formatAmount(remaining), eventTitle, label,
                    withFee ? ", frais de retrait inclus" : ""));
            if (paymentLink != null) {
                message.append(" Payez directement ici : ").append(paymentLink);
            }
        }
        return message.toString();
    }

    private String formatAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private String resolveRecipientContact(GroupMember member) {
        String email = member.getContactEmail();
        if (email != null && !email.isBlank()) {
            return email;
        }
        return member.getContactPhone();
    }
}
