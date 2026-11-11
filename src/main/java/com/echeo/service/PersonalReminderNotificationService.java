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
 * Sends personal reminders at the exact time chosen by the user
 * (due_date + due_time). Unlike a single daily task, this job runs every minute
 * and compares the exact due time with the current instant, so the chosen time is
 * respected instead of a grouped morning send.
 *
 * - If due_time is not set, the e-mail is sent at DEFAULT_TIME (08:00), to stay
 *   consistent with reminders created before this field existed.
 * - notification_sent_at is an anti-duplicate lock: a reminder is sent only once
 *   (see ReminderService for the re-arming when the date/time is modified).
 * - Paused reminders are skipped.
 * - The "due_date <= today" filter (not "= today") gives an automatic catch-up:
 *   if the server was down at the planned time, the reminder is sent as soon as it
 *   is back instead of being lost.
 */
@Service
public class PersonalReminderNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PersonalReminderNotificationService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalTime DEFAULT_TIME = LocalTime.of(8, 0);

    private final PersonalReminderRepository reminderRepository;
    private final NotificationDispatchService dispatchService;
    private final EmailService emailService;
    private final ReminderService reminderService;

    @Value("${echeo.frontend.base-url:https://echeo-one.vercel.app}")
    private String frontendBaseUrl;

    public PersonalReminderNotificationService(PersonalReminderRepository reminderRepository,
                                                NotificationDispatchService dispatchService,
                                                EmailService emailService,
                                                ReminderService reminderService) {
        this.reminderRepository = reminderRepository;
        this.dispatchService = dispatchService;
        this.emailService = emailService;
        this.reminderService = reminderService;
    }

    /**
     * Runs every minute. The volume per user stays low (personal reminders), so
     * polling at this frequency is safe for performance.
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void sendDueReminders() {
        java.time.ZoneId zone = java.time.ZoneId.of("Africa/Douala");
        LocalDate today = LocalDate.now(zone);
        LocalDateTime now = LocalDateTime.now(zone);

        List<PersonalReminder> candidates =
                reminderRepository.findByCompletedFalseAndNotificationSentAtIsNullAndDueDateLessThanEqual(today);

        log.info("Rappels perso: {} candidat(s) (due_date<= {}, now={})",
                candidates.size(), today, now);

        int sent = 0;
        int skippedTime = 0;
        for (PersonalReminder reminder : candidates) {
            if (reminder.isPaused()) {
                continue;
            }
            LocalTime effectiveTime = reminder.getDueTime() != null ? reminder.getDueTime() : DEFAULT_TIME;
            LocalDateTime triggerAt = reminder.getDueDate().atTime(effectiveTime);

            // Date already past (yesterday or before) -> send immediately, without waiting for the time.
            // Date = today -> wait for the chosen time.
            boolean dateFullyPast = reminder.getDueDate().isBefore(today);
            if (!dateFullyPast && now.isBefore(triggerAt)) {
                skippedTime++;
                log.debug("Rappel {} « {} » reporté: déclenchement à {}",
                        reminder.getId(), reminder.getTitle(), triggerAt);
                continue;
            }

            log.info("Envoi rappel perso id={} « {} » due={} {} (notification_sent_at={})",
                    reminder.getId(), reminder.getTitle(),
                    reminder.getDueDate(), effectiveTime, reminder.getNotificationSentAt());
            sendReminderEmail(reminder);
            sent++;
        }
        if (candidates.size() > 0) {
            log.info("Rappels perso: envoyés={}, reportés (heure pas atteinte)={}", sent, skippedTime);
        }

        // Recurrence without money: advance automatically if the due date passed without "Done"
        try {
            int rolled = reminderService.rollForwardOverdueRecurring(today, now);
            if (rolled > 0) {
                log.info("Rappels récurrents avancés automatiquement : {}", rolled);
            }
        } catch (Exception ex) {
            log.warn("Échec roll-forward rappels récurrents : {}", ex.getMessage());
        }
    }

    private void sendReminderEmail(PersonalReminder reminder) {
        User user = reminder.getUser();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Rappel personnel {} sans utilisateur/email valide, envoi ignoré.", reminder.getId());
            return;
        }

        // Old reminders (before V9) may have no token: generate one at send time so the
        // "mark as done" link always works.
        if (reminder.getPublicCompletionToken() == null) {
            reminder.setPublicCompletionToken(java.util.UUID.randomUUID());
            reminderRepository.save(reminder);
        }

        String message = buildReminderMessage(reminder);

        String locale = user.getPreferredLocale() != null ? user.getPreferredLocale() : "fr";
        String subject = "en".equalsIgnoreCase(locale)
                ? ("ÉCHÉO — Reminder: " + reminder.getTitle())
                : ("ÉCHÉO — Rappel : " + reminder.getTitle());
        // The send is logged; if it fails it is retried automatically
        // (NotificationDispatchService), so the reminder is considered handled.
        dispatchService.sendEmail(user.getEmail(), subject, message);

        reminder.setNotificationSentAt(OffsetDateTime.now());
        reminderRepository.save(reminder);
    }

    private String buildReminderMessage(PersonalReminder reminder) {
        User user = reminder.getUser();
        String locale = user != null && user.getPreferredLocale() != null
                ? user.getPreferredLocale() : "fr";
        boolean en = "en".equalsIgnoreCase(locale);
        String base = frontendBaseUrl == null ? "https://echeo-one.vercel.app"
                : frontendBaseUrl.replaceAll("/+$", "");

        StringBuilder message = new StringBuilder();
        if (en) {
            message.append("Hello ").append(user.getFullName()).append(", ");
            message.append("today: \"").append(reminder.getTitle()).append("\"");
        } else {
            message.append("Bonjour ").append(user.getFullName()).append(", ");
            message.append("c'est aujourd'hui : \"").append(reminder.getTitle()).append("\"");
        }
        if (reminder.getDueTime() != null) {
            message.append(" (").append(reminder.getDueTime().format(TIME_FORMAT)).append(")");
        }
        message.append(".");
        if (reminder.getDescription() != null && !reminder.getDescription().isBlank()) {
            message.append(" ").append(reminder.getDescription());
        }
        if (reminder.getPublicCompletionToken() != null) {
            String completionLink = base + "/reminders/complete/" + reminder.getPublicCompletionToken();
            if (en) {
                message.append(" Click here to mark this reminder as done: ").append(completionLink);
            } else {
                message.append(" Clique ici pour marquer ce rappel comme fait : ").append(completionLink);
            }
        }
        return message.toString();
    }
}
