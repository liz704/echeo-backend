package com.echeo.service;

import com.echeo.dto.WeekPlanSubmitRequest;
import com.echeo.dto.WeekPlanTaskRequest;
import com.echeo.exception.EntityNotFoundException;
import com.echeo.exception.InvalidArgumentException;
import com.echeo.model.entity.PersonalReminder;
import com.echeo.model.entity.User;
import com.echeo.model.enums.RepetitionType;
import com.echeo.repository.PersonalReminderRepository;
import com.echeo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WeekPlanService {

    private static final Logger log = LoggerFactory.getLogger(WeekPlanService.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");

    private final UserRepository userRepository;
    private final PersonalReminderRepository reminderRepository;
    private final EmailService emailService;

    @Value("${echeo.frontend.base-url:https://echeo-one.vercel.app}")
    private String frontendBaseUrl;

    public WeekPlanService(UserRepository userRepository,
                           PersonalReminderRepository reminderRepository,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.reminderRepository = reminderRepository;
        this.emailService = emailService;
    }

    public User getByToken(UUID token) {
        return userRepository.findByWeekPlanToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Lien de planification invalide ou expiré."));
    }

    @Transactional
    public List<PersonalReminder> submitTasks(UUID token, WeekPlanSubmitRequest request) {
        User user = getByToken(token);
        if (request.getTasks() == null || request.getTasks().isEmpty()) {
            throw new InvalidArgumentException("Ajoute au moins une tâche.");
        }
        LocalTime defaultTime = request.getDefaultTime() != null
                ? request.getDefaultTime() : LocalTime.of(8, 0);

        List<PersonalReminder> created = new ArrayList<>();
        for (WeekPlanTaskRequest task : request.getTasks()) {
            if (task.getTitle() == null || task.getTitle().isBlank()) {
                continue;
            }
            if (task.getDueDate() == null) {
                throw new InvalidArgumentException("Chaque tâche doit avoir une date.");
            }
            PersonalReminder r = new PersonalReminder();
            r.setUser(user);
            r.setTitle(task.getTitle().trim());
            r.setDescription(task.getDescription());
            r.setDueDate(task.getDueDate());
            r.setDueTime(task.getDueTime() != null ? task.getDueTime() : defaultTime);
            r.setRepetitionType(RepetitionType.NONE);
            r.setCompleted(false);
            r.setPublicCompletionToken(UUID.randomUUID());
            created.add(reminderRepository.save(r));
        }
        if (created.isEmpty()) {
            throw new InvalidArgumentException("Ajoute au moins une tâche avec un titre.");
        }
        return created;
    }

    /**
     * Chaque minute : envoie l'e-mail de planification aux users qui l'ont activé,
     * le jour et l'heure choisis (fuseau WAT).
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void sendWeeklyPlanEmails() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        DayOfWeek today = now.getDayOfWeek(); // MONDAY=1 … SUNDAY=7
        int isoDay = today.getValue();
        LocalTime nowTime = now.toLocalTime().withSecond(0).withNano(0);

        for (User user : userRepository.findByWeekPlanEnabledTrue()) {
            if (user.getWeekPlanDay() != isoDay) {
                continue;
            }
            LocalTime sendAt = user.getWeekPlanSendTime() != null
                    ? user.getWeekPlanSendTime().withSecond(0).withNano(0)
                    : LocalTime.of(8, 0);
            if (nowTime.isBefore(sendAt)) {
                continue;
            }
            // Une fois par jour max
            if (user.getWeekPlanLastSentAt() != null
                    && user.getWeekPlanLastSentAt().atZoneSameInstant(ZONE).toLocalDate().equals(now.toLocalDate())) {
                continue;
            }
            try {
                ensureToken(user);
                sendEmail(user);
                user.setWeekPlanLastSentAt(OffsetDateTime.now());
                userRepository.save(user);
            } catch (Exception ex) {
                log.warn("Échec e-mail week-plan user {} : {}", user.getId(), ex.getMessage());
            }
        }
    }

    private void ensureToken(User user) {
        if (user.getWeekPlanToken() == null) {
            user.setWeekPlanToken(UUID.randomUUID());
            userRepository.save(user);
        }
    }

    private void sendEmail(User user) {
        String base = frontendBaseUrl == null ? "https://echeo-one.vercel.app"
                : frontendBaseUrl.replaceAll("/+$", "");
        String link = base + "/week-plan/" + user.getWeekPlanToken();
        boolean en = user.getPreferredLocale() != null && user.getPreferredLocale().equalsIgnoreCase("en");
        String subject = en ? "ÉCHÉO — Plan your week" : "ÉCHÉO — Planifie ta semaine";
        String body = en
                ? ("Hello " + user.getFullName() + ",\n\nClick here to enter your tasks for the week:\n" + link + "\n")
                : ("Bonjour " + user.getFullName() + ",\n\nClique ici pour saisir tes tâches de la semaine :\n" + link + "\n");
        emailService.send(user.getEmail(), subject, body);
        log.info("Week-plan email sent to {}", user.getEmail());
    }

    @Transactional
    public User updateWeekPlanSettings(Long userId, boolean enabled, int day, LocalTime sendTime) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User", userId));
        if (day < 1 || day > 7) {
            throw new InvalidArgumentException("Le jour doit être entre 1 (lundi) et 7 (dimanche).");
        }
        user.setWeekPlanEnabled(enabled);
        user.setWeekPlanDay(day);
        user.setWeekPlanSendTime(sendTime != null ? sendTime : LocalTime.of(8, 0));
        if (enabled && user.getWeekPlanToken() == null) {
            user.setWeekPlanToken(UUID.randomUUID());
        }
        return userRepository.save(user);
    }
}
