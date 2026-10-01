package com.echeo.service;

import com.echeo.model.entity.NotificationLog;
import com.echeo.model.entity.PersonalReminder;
import com.echeo.model.entity.User;
import com.echeo.model.enums.NotificationStatus;
import com.echeo.model.enums.NotificationType;
import com.echeo.repository.NotificationLogRepository;
import com.echeo.repository.PersonalReminderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Envoi des rappels personnels à l'heure précise réglée par l'utilisateur
 * (due_date + due_time). Contrairement à une tâche unique quotidienne, ce
 * job tourne toutes les minutes et compare l'échéance exacte à l'instant
 * présent, pour respecter l'heure choisie au lieu d'un envoi groupé le matin.
 *
 * - Si due_time n'est pas renseignée, l'envoi a lieu à DEFAULT_TIME (08:00),
 *   pour rester cohérent avec les rappels créés avant l'ajout de ce champ.
 * - notification_sent_at sert de verrou anti-doublon : un rappel n'est
 *   envoyé qu'une seule fois (voir ReminderService pour le ré-armement en
 *   cas de modification de la date/heure).
 * - Le filtre "due_date <= aujourd'hui" (et non "= aujourd'hui") permet un
 *   rattrapage automatique : si le serveur a été indisponible pendant
 *   l'heure prévue, le rappel part dès que possible au retour, plutôt que
 *   d'être perdu.
 */
@Service
public class PersonalReminderNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PersonalReminderNotificationService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalTime DEFAULT_TIME = LocalTime.of(8, 0);

    private final PersonalReminderRepository reminderRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final EmailService emailService;

    @Value("${echeo.frontend.base-url:https://echeo-one.vercel.app}")
    private String frontendBaseUrl;

    public PersonalReminderNotificationService(PersonalReminderRepository reminderRepository,
                                                NotificationLogRepository notificationLogRepository,
                                                EmailService emailService) {
        this.reminderRepository = reminderRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.emailService = emailService;
    }

    /**
     * Exécutée toutes les minutes. Le volume par utilisateur reste faible
     * (rappels personnels), donc scruter à cette fréquence est sans risque
     * de performance.
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void sendDueReminders() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<PersonalReminder> candidates =
                reminderRepository.findByCompletedFalseAndNotificationSentAtIsNullAndDueDateLessThanEqual(today);

        for (PersonalReminder reminder : candidates) {
            LocalTime effectiveTime = reminder.getDueTime() != null ? reminder.getDueTime() : DEFAULT_TIME;
            LocalDateTime triggerAt = reminder.getDueDate().atTime(effectiveTime);

            // Pas encore l'heure réglée (rappel du jour même, mais plus tard) : on attend.
            if (now.isBefore(triggerAt)) {
                continue;
            }

            sendReminderEmail(reminder);
        }
    }

    private void sendReminderEmail(PersonalReminder reminder) {
        User user = reminder.getUser();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Rappel personnel {} sans utilisateur/email valide, envoi ignoré.", reminder.getId());
            return;
        }

        // Anciens rappels (avant V9) peuvent n'avoir aucun jeton : on en génère
        // un à l'envoi pour que le lien "marquer fait" fonctionne toujours.
        if (reminder.getPublicCompletionToken() == null) {
            reminder.setPublicCompletionToken(java.util.UUID.randomUUID());
            reminderRepository.save(reminder);
        }

        String message = buildReminderMessage(reminder);

        NotificationLog notificationLog = new NotificationLog();
        notificationLog.setRecipientContact(user.getEmail());
        notificationLog.setType(NotificationType.EMAIL);
        notificationLog.setMessageContent(message);
        notificationLog.setStatus(NotificationStatus.PENDING);
        notificationLogRepository.save(notificationLog);

        try {
            String messageId = emailService.send(user.getEmail(), "ÉCHÉO — Rappel : " + reminder.getTitle(), message);
            notificationLog.setProviderMessageId(messageId);
            notificationLog.setStatus(NotificationStatus.SENT);
        } catch (Exception ex) {
            log.error("Échec de l'envoi du rappel personnel {} à {} : {}",
                    reminder.getId(), user.getEmail(), ex.getMessage(), ex);
            notificationLog.setStatus(NotificationStatus.FAILED);
            // On ne marque PAS notificationSentAt si l'envoi échoue : le job
            // retentera à la prochaine minute (nouveau rattrapage).
            notificationLogRepository.save(notificationLog);
            return;
        }
        notificationLog.setSentAt(OffsetDateTime.now());
        notificationLogRepository.save(notificationLog);

        reminder.setNotificationSentAt(OffsetDateTime.now());
        reminderRepository.save(reminder);
    }

    private String buildReminderMessage(PersonalReminder reminder) {
        StringBuilder message = new StringBuilder();
        message.append("Bonjour ").append(reminder.getUser().getFullName()).append(", ");
        message.append("c'est aujourd'hui : \"").append(reminder.getTitle()).append("\"");
        if (reminder.getDueTime() != null) {
            message.append(" (").append(reminder.getDueTime().format(TIME_FORMAT)).append(")");
        }
        message.append(".");
        if (reminder.getDescription() != null && !reminder.getDescription().isBlank()) {
            message.append(" ").append(reminder.getDescription());
        }
        if (reminder.getPublicCompletionToken() != null) {
            String completionLink = frontendBaseUrl + "/reminders/complete/" + reminder.getPublicCompletionToken();
            message.append(" Clique ici pour marquer ce rappel comme fait : ").append(completionLink);
        }
        return message.toString();
    }
}
