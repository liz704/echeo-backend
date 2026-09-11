package com.echeo.service;

import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.entity.Group;
import com.echeo.model.entity.GroupEvent;
import com.echeo.model.entity.GroupMember;
import com.echeo.model.entity.NotificationLog;
import com.echeo.model.enums.NotificationStatus;
import com.echeo.model.enums.NotificationType;
import com.echeo.model.enums.PaymentStatus;
import com.echeo.repository.EventMemberStatusRepository;
import com.echeo.repository.GroupEventRepository;
import com.echeo.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tâche planifiée quotidienne de relance des paiements de groupe.
 * Scrute les événements dont l'échéance (event_date) tombe à J-7, J-3 ou J0,
 * et prépare/journalise une relance personnalisée pour chaque membre
 * dont le statut n'est pas encore soldé (PENDING, PARTIALLY_PAID, OVERDUE).
 * Le propriétaire du groupe reçoit une copie de chaque relance, sauf s'il
 * a désactivé cette préférence (Group.notifyOwnerOnReminders).
 */
@Service
public class NotificationSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchedulerService.class);

    // Statuts considérés comme "à relancer" : un membre SURPLUS ou PAID n'a plus rien à devoir.
    private static final List<PaymentStatus> RELANCE_STATUSES = List.of(
            PaymentStatus.PENDING, PaymentStatus.PARTIALLY_PAID, PaymentStatus.OVERDUE
    );

    private final GroupEventRepository groupEventRepository;
    private final EventMemberStatusRepository eventMemberStatusRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final EmailService emailService;

    public NotificationSchedulerService(GroupEventRepository groupEventRepository,
                                         EventMemberStatusRepository eventMemberStatusRepository,
                                         NotificationLogRepository notificationLogRepository,
                                         EmailService emailService) {
        this.groupEventRepository = groupEventRepository;
        this.eventMemberStatusRepository = eventMemberStatusRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.emailService = emailService;
    }

    /**
     * Exécutée tous les jours à 08h00 (heure du serveur).
     * Cron Spring : seconde minute heure jour-du-mois mois jour-de-semaine.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void runDailyPaymentReminders() {
        LocalDate today = LocalDate.now();

        // Avant l'échéance : rappels préventifs.
        processEchéance(today.plusDays(7), "J-7");
        processEchéance(today.plusDays(3), "J-3");
        processEchéance(today, "J0");

        // Après l'échéance : relances en rafale pour les impayés (le
        // passage en OVERDUE est fait juste avant, à 7h, par
        // GroupService.markOverdueStatuses — voir son cron).
        processEchéance(today.minusDays(1), "J+1 (en retard)");
        processEchéance(today.minusDays(3), "J+3 (en retard)");
        processEchéance(today.minusDays(7), "J+7 (en retard)");
    }

    /**
     * Traite toutes les échéances (événements de groupe) tombant à la date donnée,
     * et déclenche une relance pour chaque membre encore redevable.
     */
    @Transactional
    public void processEchéance(LocalDate targetDate, String label) {
        List<GroupEvent> events = groupEventRepository.findByEventDate(targetDate);

        for (GroupEvent event : events) {
            List<EventMemberStatus> unpaidStatuses =
                    eventMemberStatusRepository.findByEvent_IdAndStatusIn(event.getId(), RELANCE_STATUSES);

            for (EventMemberStatus status : unpaidStatuses) {
                sendPaymentReminder(event, status, label);
            }
        }
    }

    /**
     * Construit le message personnalisé, journalise la relance en base
     * (notification_logs), tente l'envoi effectif, puis envoie une copie
     * récapitulative au propriétaire du groupe (sauf préférence contraire).
     */
    private void sendPaymentReminder(GroupEvent event, EventMemberStatus status, String label) {
        GroupMember member = status.getGroupMember();
        BigDecimal required = status.getRequiredAmount() != null ? status.getRequiredAmount() : BigDecimal.ZERO;
        BigDecimal paid = status.getPaidAmount() != null ? status.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal remaining = required.subtract(paid);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }

        String memberMessage = buildReminderMessage(
                member.getContactFullName(), remaining, required, event.getTitle(), label);

        NotificationLog memberLog = logAndSend(resolveRecipientContact(member), memberMessage);

        Group group = event.getGroup();
        if (group.isNotifyOwnerOnReminders()) {
            String ownerMessage = String.format(
                    "Relance envoyée à %s (%s) pour l'événement '%s' : reste %s FCFA sur %s FCFA (échéance %s).",
                    member.getContactFullName(), resolveRecipientContact(member), event.getTitle(),
                    formatAmount(remaining), formatAmount(required), label
            );
            logAndSend(group.getOwner().getEmail(), ownerMessage);
        }
    }

    private NotificationLog logAndSend(String recipientContact, String message) {
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
                                         String eventTitle, String label) {
        return String.format(
                "Bonjour %s, il vous reste %s FCFA sur %s FCFA pour l'événement '%s' (échéance %s).",
                fullName, formatAmount(remaining), formatAmount(total), eventTitle, label
        );
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
                String messageId = emailService.send(
                        notificationLog.getRecipientContact(),
                        "ÉCHÉO — Rappel de paiement",
                        notificationLog.getMessageContent()
                );
                notificationLog.setProviderMessageId(messageId);
                return true;
            } catch (Exception ex) {
                log.warn("Échec de l'envoi de la relance email à {} : {}",
                        notificationLog.getRecipientContact(), ex.getMessage());
                return false;
            }
        }

        // SMS / WHATSAPP : toujours simulé à ce stade.
        log.info("Relance envoyée (simulation, {}) à {} : {}",
                notificationLog.getType(), notificationLog.getRecipientContact(), notificationLog.getMessageContent());
        return true;
    }
}
