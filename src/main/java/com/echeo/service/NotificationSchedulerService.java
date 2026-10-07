package com.echeo.service;

import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.entity.Group;
import com.echeo.model.entity.GroupEvent;
import com.echeo.model.entity.GroupMember;
import com.echeo.model.entity.NotificationLog;
import com.echeo.model.entity.PaymentToken;
import com.echeo.model.enums.NotificationStatus;
import com.echeo.model.enums.NotificationType;
import com.echeo.model.enums.PaymentStatus;
import com.echeo.repository.EventMemberStatusRepository;
import com.echeo.repository.GroupEventRepository;
import com.echeo.repository.NotificationLogRepository;
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
 * Tâche planifiée de relance des paiements de groupe.
 * Scrute les événements dont l'échéance (event_date) tombe à J-7, J-3 ou J0,
 * et prépare/journalise une relance personnalisée pour chaque membre
 * dont le statut n'est pas encore soldé (PENDING, PARTIALLY_PAID, OVERDUE).
 * Le propriétaire du groupe reçoit une copie de chaque relance, sauf s'il
 * a désactivé cette préférence (Group.notifyOwnerOnReminders).
 *
 * Contrairement à une exécution unique quotidienne, ce job tourne toutes les
 * minutes afin de respecter l'heure précise réglée par le créateur sur
 * l'événement (GroupEvent.eventTime — 08:00 par défaut si absente). Pour
 * éviter d'envoyer la même relance en boucle tant que la minute matche,
 * chaque EventMemberStatus mémorise la dernière étape envoyée
 * (lastReminderStage / lastReminderSentAt) : une étape donnée ("J-7", "J0",
 * "J+3 (en retard)"...) n'est envoyée qu'une fois par jour et par membre.
 * Le filtre "l'heure de déclenchement est déjà passée" (et non "= l'heure
 * exacte") permet aussi un rattrapage automatique en cas d'indisponibilité
 * temporaire du serveur.
 */
@Service
public class NotificationSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchedulerService.class);
    private static final LocalTime DEFAULT_TIME = LocalTime.of(8, 0);

    // Statuts considérés comme "à relancer" : un membre SURPLUS ou PAID n'a plus rien à devoir.
    private static final List<PaymentStatus> RELANCE_STATUSES = List.of(
            PaymentStatus.PENDING, PaymentStatus.PARTIALLY_PAID, PaymentStatus.OVERDUE
    );

    // Idem pour un événement sans argent : NOT_SEEN et OVERDUE (jamais vu à
    // temps) sont à relancer ; SEEN n'a plus besoin de l'être.
    private static final List<PaymentStatus> RELANCE_STATUSES_SANS_ARGENT = List.of(
            PaymentStatus.NOT_SEEN, PaymentStatus.OVERDUE
    );

    // Un lien de paiement généré pour une relance reste valable jusqu'à la
    // prochaine étape (au pire 7 jours, pour la relance J-7).
    private static final Duration PAYMENT_LINK_VALIDITY = Duration.ofDays(10);

    private final GroupEventRepository groupEventRepository;
    private final EventMemberStatusRepository eventMemberStatusRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final EmailService emailService;
    private final PaymentService paymentService;

    @Value("${echeo.frontend.base-url:https://echeo-one.vercel.app}")
    private String frontendBaseUrl;

    public NotificationSchedulerService(GroupEventRepository groupEventRepository,
                                         EventMemberStatusRepository eventMemberStatusRepository,
                                         NotificationLogRepository notificationLogRepository,
                                         EmailService emailService,
                                         PaymentService paymentService) {
        this.groupEventRepository = groupEventRepository;
        this.eventMemberStatusRepository = eventMemberStatusRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.emailService = emailService;
        this.paymentService = paymentService;
    }

    /**
     * Exécutée toutes les minutes (heure du serveur).
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void runGroupPaymentReminders() {
        java.time.ZoneId zone = java.time.ZoneId.of("Africa/Douala");
        LocalDate today = LocalDate.now(zone);

        // Avant l'échéance : rappels préventifs (date exacte).
        processEchéance(today.plusDays(7), "J-7", zone);
        processEchéance(today.plusDays(3), "J-3", zone);
        processEchéance(today, "J0", zone);

        // Après l'échéance : TOUS les événements en retard (impayé / partiel),
        // une relance par jour jusqu'au paiement (rattrapage si le serveur dormait).
        processAllOverdue(today, zone);
    }

    /**
     * Relance quotidienne pour tout événement dont la date est passée et dont
     * des membres sont encore PENDING / PARTIALLY_PAID / OVERDUE (ou NOT_SEEN
     * sans argent). Une seule fois par jour et par membre (lastReminderSentAt).
     */
    @Transactional
    public void processAllOverdue(LocalDate today, java.time.ZoneId zone) {
        List<GroupEvent> overdueEvents = groupEventRepository.findByEventDateBefore(today);
        LocalDateTime now = LocalDateTime.now(zone);

        for (GroupEvent event : overdueEvents) {
            long daysLate = java.time.temporal.ChronoUnit.DAYS.between(event.getEventDate(), today);
            if (daysLate < 1) {
                continue;
            }
            // Jours ciblés : 1, 3, 5, 7, puis chaque jour à partir de J+7,
            // et après J+21 une fois par semaine (J+28, J+35…).
            // + rattrapage : si le serveur a dormi un jour clé, on envoie dès
            // le prochain réveil (label basé sur le vrai nombre de jours de retard).
            if (!shouldSendOverdueToday(daysLate)) {
                continue;
            }

            String label = "J+" + daysLate + " (en retard)";
            LocalTime effectiveTime = event.getEventTime() != null ? event.getEventTime() : DEFAULT_TIME;
            LocalDateTime triggerAt = today.atTime(effectiveTime);
            if (now.isBefore(triggerAt)) {
                continue;
            }

            boolean hasMoney = event.hasMoney();
            List<PaymentStatus> relevantStatuses = hasMoney ? RELANCE_STATUSES : RELANCE_STATUSES_SANS_ARGENT;
            List<EventMemberStatus> toRemind =
                    eventMemberStatusRepository.findByEvent_IdAndStatusIn(event.getId(), relevantStatuses);

            for (EventMemberStatus status : toRemind) {
                // Une relance max par jour calendaire (WAT), quel que soit le label.
                if (alreadySentAnyToday(status, zone)) {
                    continue;
                }
                boolean sent = hasMoney
                        ? sendPaymentReminder(event, status, label)
                        : sendAcknowledgmentReminder(event, status, label);
                if (sent) {
                    status.setLastReminderStage(label);
                    status.setLastReminderSentAt(OffsetDateTime.now(zone));
                    eventMemberStatusRepository.save(status);
                    log.info("Relance retard envoyée: event={}, memberStatus={}, {}",
                            event.getId(), status.getId(), label);
                }
            }
        }
    }

    /**
     * J+1, J+3, J+5, J+7, puis tous les jours jusqu'à J+21, puis hebdo.
     * Pour le rattrapage après sommeil : si on a dépassé un jalon sans envoi
     * (ex. réveil à J+4), on envoie quand même (daysLate correspond à un
     * jour "actif" ou on force l'envoi si aucun envoi depuis > 1 jour — géré
     * par alreadySentAnyToday + shouldSend).
     */
    private boolean shouldSendOverdueToday(long daysLate) {
        if (daysLate == 1 || daysLate == 3 || daysLate == 5 || daysLate == 7) {
            return true;
        }
        if (daysLate > 7 && daysLate <= 21) {
            return true; // quotidien entre J+8 et J+21
        }
        if (daysLate > 21) {
            return (daysLate - 21) % 7 == 0; // J+28, J+35…
        }
        // J+2, J+4, J+6 : aussi pour ne pas perdre les retards si le serveur
        // a dormi le jour J+1 / J+3 / J+5.
        return daysLate == 2 || daysLate == 4 || daysLate == 6;
    }

    private boolean alreadySentAnyToday(EventMemberStatus status, java.time.ZoneId zone) {
        OffsetDateTime last = status.getLastReminderSentAt();
        if (last == null) {
            return false;
        }
        return last.atZoneSameInstant(zone).toLocalDate().isEqual(LocalDate.now(zone));
    }

    /**
     * Traite toutes les échéances (événements de groupe) tombant à la date donnée,
     * et déclenche une relance pour chaque membre encore redevable — mais
     * seulement une fois l'heure réglée sur l'événement atteinte, et une
     * seule fois par jour par membre pour cette étape (label).
     */
    @Transactional
    public void processEchéance(LocalDate targetDate, String label, java.time.ZoneId zone) {
        List<GroupEvent> events = groupEventRepository.findByEventDate(targetDate);
        LocalDateTime now = LocalDateTime.now(zone);

        for (GroupEvent event : events) {
            LocalTime effectiveTime = event.getEventTime() != null ? event.getEventTime() : DEFAULT_TIME;
            LocalDateTime triggerAt = LocalDate.now(zone).atTime(effectiveTime);

            // Pas encore l'heure réglée aujourd'hui : on attend la prochaine minute.
            if (now.isBefore(triggerAt)) {
                continue;
            }

            boolean hasMoney = event.hasMoney();
            List<PaymentStatus> relevantStatuses = hasMoney ? RELANCE_STATUSES : RELANCE_STATUSES_SANS_ARGENT;
            List<EventMemberStatus> toRemind =
                    eventMemberStatusRepository.findByEvent_IdAndStatusIn(event.getId(), relevantStatuses);

            for (EventMemberStatus status : toRemind) {
                if (alreadySentToday(status, label)) {
                    continue;
                }
                boolean sent = hasMoney
                        ? sendPaymentReminder(event, status, label)
                        : sendAcknowledgmentReminder(event, status, label);
                if (sent) {
                    // On ne verrouille l'étape que si l'envoi au membre a
                    // réussi : sinon le job retentera à la prochaine minute.
                    status.setLastReminderStage(label);
                    status.setLastReminderSentAt(OffsetDateTime.now());
                    eventMemberStatusRepository.save(status);
                }
            }
        }
    }

    private boolean alreadySentToday(EventMemberStatus status, String label) {
        if (!label.equals(status.getLastReminderStage())) {
            return false;
        }
        OffsetDateTime last = status.getLastReminderSentAt();
        if (last == null) {
            return false;
        }
        java.time.ZoneId zone = java.time.ZoneId.of("Africa/Douala");
        return last.atZoneSameInstant(zone).toLocalDate().isEqual(LocalDate.now(zone));
    }

    /**
     * Construit le message personnalisé, journalise la relance en base
     * (notification_logs), tente l'envoi effectif, puis envoie une copie
     * récapitulative au propriétaire du groupe (sauf préférence contraire).
     */

    private String resolveMemberLocale(GroupMember member) {
        if (member != null && member.getUser() != null && member.getUser().getPreferredLocale() != null) {
            return member.getUser().getPreferredLocale();
        }
        return "fr";
    }

    private String frontendBase() {
        String base = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "https://echeo-one.vercel.app" : frontendBaseUrl;
        return base.replaceAll("/+$", "");
    }

    private boolean sendPaymentReminder(GroupEvent event, EventMemberStatus status, String label) {
        GroupMember member = status.getGroupMember();
        BigDecimal required = status.getRequiredAmount() != null ? status.getRequiredAmount() : BigDecimal.ZERO;
        BigDecimal paid = status.getPaidAmount() != null ? status.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal remaining = required.subtract(paid);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }

        // Lien de paiement public : un jeton frais à chaque relance (le
        // précédent, s'il existe, reste valable jusqu'à expiration — pas
        // besoin de l'invalider, un jeton payé une fois se marque "used").
        String paymentLink;
        try {
            PaymentToken token = paymentService.generatePaymentToken(status.getId(), PAYMENT_LINK_VALIDITY);
            paymentLink = frontendBase() + "/pay/" + token.getTokenUuid();
        } catch (Exception ex) {
            log.warn("Échec de la génération du lien de paiement pour le statut {} : {}", status.getId(), ex.getMessage());
            paymentLink = null;
        }

        String locale = resolveMemberLocale(member);
        String memberMessage = buildReminderMessage(
                member.getContactFullName(), remaining, required, event.getTitle(), label, paymentLink, locale);

        NotificationLog memberLog = logAndSend(resolveRecipientContact(member), memberMessage, locale);

        Group group = event.getGroup();
        if (group.isNotifyOwnerOnReminders()) {
            String ownerLocale = group.getOwner() != null ? group.getOwner().getPreferredLocale() : "fr";
            boolean en = ownerLocale != null && ownerLocale.equalsIgnoreCase("en");
            String ownerMessage = en
                    ? String.format("Reminder sent to %s (%s) for '%s': %s FCFA left of %s FCFA (due %s).",
                        member.getContactFullName(), resolveRecipientContact(member), event.getTitle(),
                        formatAmount(remaining), formatAmount(required), label)
                    : String.format("Relance envoyée à %s (%s) pour l'événement '%s' : reste %s FCFA sur %s FCFA (échéance %s).",
                        member.getContactFullName(), resolveRecipientContact(member), event.getTitle(),
                        formatAmount(remaining), formatAmount(required), label);
            logAndSend(group.getOwner().getEmail(), ownerMessage, ownerLocale);
        }

        return memberLog.getStatus() == NotificationStatus.SENT;
    }

    /**
     * Rappel pour un événement SANS argent : pas de montant, juste
     * l'information et un lien "j'ai vu ce message" (accusé de lecture, pas
     * de confirmation supplémentaire nécessaire côté membre).
     */
    private boolean sendAcknowledgmentReminder(GroupEvent event, EventMemberStatus status, String label) {
        GroupMember member = status.getGroupMember();

        String ackLink = status.getPublicAckToken() != null
                ? frontendBase() + "/ack/" + status.getPublicAckToken()
                : null;

        String locale = resolveMemberLocale(member);
        boolean en = locale.equalsIgnoreCase("en");
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

        NotificationLog memberLog = logAndSend(resolveRecipientContact(member), message.toString(), locale);

        Group group = event.getGroup();
        if (group.isNotifyOwnerOnReminders()) {
            String ownerLocale = group.getOwner() != null ? group.getOwner().getPreferredLocale() : "fr";
            boolean ownerEn = ownerLocale != null && ownerLocale.equalsIgnoreCase("en");
            String ownerMessage = ownerEn
                    ? String.format("Reminder sent to %s (%s) for '%s' (due %s, not seen yet).",
                        member.getContactFullName(), resolveRecipientContact(member), event.getTitle(), label)
                    : String.format("Rappel envoyé à %s (%s) pour l'événement '%s' (échéance %s, pas encore vu).",
                        member.getContactFullName(), resolveRecipientContact(member), event.getTitle(), label);
            logAndSend(group.getOwner().getEmail(), ownerMessage, ownerLocale);
        }

        return memberLog.getStatus() == NotificationStatus.SENT;
    }

    private NotificationLog logAndSend(String recipientContact, String message, String locale) {
        NotificationLog notificationLog = new NotificationLog();
        notificationLog.setRecipientContact(recipientContact);
        notificationLog.setType(NotificationType.EMAIL);
        notificationLog.setMessageContent(message);
        notificationLog.setStatus(NotificationStatus.PENDING);
        notificationLogRepository.save(notificationLog);

        boolean sentSuccessfully = dispatch(notificationLog);

        notificationLog.setSentAt(OffsetDateTime.now());
        notificationLog.setStatus(sentSuccessfully ? NotificationStatus.SENT : NotificationStatus.FAILED);
        return notificationLogRepository.save(notificationLog);
    }

    /**
     * Construit le texte de relance personnalisé.
     * Exemple : "Bonjour Awa, il vous reste 15 000 FCFA sur 50 000 FCFA pour
     * l'événement 'Cotisation mariage' (échéance J-3)."
     */
    private String buildReminderMessage(String fullName, BigDecimal remaining, BigDecimal total,
                                         String eventTitle, String label, String paymentLink,
                                         String locale) {
        boolean en = locale != null && locale.equalsIgnoreCase("en");
        StringBuilder message = new StringBuilder();
        if (en) {
            message.append(String.format(
                    "Hello %s, you still owe %s FCFA out of %s FCFA for '%s' (due %s).",
                    fullName, formatAmount(remaining), formatAmount(total), eventTitle, label
            ));
            if (paymentLink != null) {
                message.append(" Pay here: ").append(paymentLink);
            }
        } else {
            message.append(String.format(
                    "Bonjour %s, il vous reste %s FCFA sur %s FCFA pour l'événement '%s' (échéance %s).",
                    fullName, formatAmount(remaining), formatAmount(total), eventTitle, label
            ));
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

    /**
     * Point d'intégration avec le fournisseur d'envoi réel. Email : envoi
     * réel via EmailService (API Brevo), messageId capturé pour le suivi de
     * livraison (voir BrevoWebhookController). SMS et WhatsApp restent
     * simulés (nécessitent un fournisseur tiers payant, ex. Twilio — non
     * configuré ici, aucune option gratuite fiable n'existe).
     */
    private boolean dispatch(NotificationLog notificationLog) {
        if (notificationLog.getType() == NotificationType.EMAIL) {
            try {
                boolean en = notificationLog.getMessageContent() != null
                        && notificationLog.getMessageContent().startsWith("Hello ");
                String subject = en ? "ÉCHÉO — Payment reminder" : "ÉCHÉO — Rappel de paiement";
                String messageId = emailService.send(
                        notificationLog.getRecipientContact(),
                        subject,
                        notificationLog.getMessageContent()
                );
                notificationLog.setProviderMessageId(messageId);
                return true;
            } catch (Exception ex) {
                log.error("Échec de l'envoi de la relance email à {} : {}",
                        notificationLog.getRecipientContact(), ex.getMessage(), ex);
                return false;
            }
        }

        // SMS / WHATSAPP : toujours simulé à ce stade.
        log.info("Relance envoyée (simulation, {}) à {} : {}",
                notificationLog.getType(), notificationLog.getRecipientContact(), notificationLog.getMessageContent());
        return true;
    }
}
