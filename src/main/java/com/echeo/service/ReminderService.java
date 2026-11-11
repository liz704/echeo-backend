package com.echeo.service;

import com.echeo.exception.EntityNotFoundException;
import com.echeo.exception.InvalidArgumentException;
import com.echeo.model.entity.PersonalReminder;
import com.echeo.model.enums.RepetitionType;
import com.echeo.repository.PersonalReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic for personal reminders: creation, update, completion with
 * recurrence handling, pause / resume, and real deletion (hard delete).
 */
@Service
public class ReminderService {

    private final PersonalReminderRepository reminderRepository;

    public ReminderService(PersonalReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    /**
     * Creates a new personal reminder after basic business validation.
     */
    @Transactional
    public PersonalReminder createReminder(PersonalReminder reminder) {
        validateReminder(reminder);
        reminder.setId(null);
        reminder.setCompleted(false);
        reminder.setPaused(false);
        reminder.setNextOccurrence(null);
        reminder.setNotificationSentAt(null);
        reminder.setPublicCompletionToken(java.util.UUID.randomUUID());
        return reminderRepository.save(reminder);
    }

    /**
     * Updates an existing reminder (title, description, date/time, recurrence).
     * Never changes the owner nor the is_completed status
     * (see markAsCompleted for the latter).
     */
    @Transactional
    public PersonalReminder updateReminder(Long reminderId, PersonalReminder updatedData) {
        if (reminderId == null) {
            throw new InvalidArgumentException("L'identifiant du rappel est obligatoire.");
        }
        validateReminder(updatedData);

        PersonalReminder existing = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new EntityNotFoundException("PersonalReminder", reminderId));

        boolean scheduleChanged = !java.util.Objects.equals(existing.getDueDate(), updatedData.getDueDate())
                || !java.util.Objects.equals(existing.getDueTime(), updatedData.getDueTime());

        existing.setTitle(updatedData.getTitle());
        existing.setDescription(updatedData.getDescription());
        existing.setDueDate(updatedData.getDueDate());
        existing.setDueTime(updatedData.getDueTime());
        existing.setRepetitionType(updatedData.getRepetitionType());

        // If the date/time changes, the reminder is re-armed so it can be notified
        // again at the new time, even if it had already been sent.
        if (scheduleChanged) {
            existing.setNotificationSentAt(null);
        }

        return reminderRepository.save(existing);
    }

    /**
     * Marks a reminder as done. If it is recurring (repetitionType != NONE), the next
     * occurrence is computed automatically and saved as a new active reminder, and the
     * computed date is recorded on the current reminder (next_occurrence) as history.
     *
     * @return the updated (completed) current reminder. The new occurrence, if any, is
     *         persisted but not returned by this method.
     */
    @Transactional
    public PersonalReminder markAsCompleted(Long reminderId) {
        if (reminderId == null) {
            throw new InvalidArgumentException("L'identifiant du rappel est obligatoire.");
        }

        PersonalReminder current = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new EntityNotFoundException("PersonalReminder", reminderId));

        // Idempotent: a second "mark as done" (double click in the app or e-mail link)
        // must not generate another recurring occurrence.
        if (current.isCompleted()) {
            return current;
        }

        current.setCompleted(true);

        RepetitionType repetition = current.getRepetitionType();
        if (repetition != null && repetition != RepetitionType.NONE) {
            LocalDate nextDate = computeNextOccurrence(current.getDueDate(), repetition);

            // History: record on the closed reminder the date of the next generated due date.
            current.setNextOccurrence(nextDate);
            reminderRepository.save(current);

            PersonalReminder nextReminder = new PersonalReminder();
            nextReminder.setUser(current.getUser());
            nextReminder.setTitle(current.getTitle());
            nextReminder.setDescription(current.getDescription());
            nextReminder.setDueDate(nextDate);
            nextReminder.setDueTime(current.getDueTime());
            nextReminder.setRepetitionType(repetition);
            nextReminder.setCompleted(false);
            nextReminder.setNextOccurrence(null);
            nextReminder.setPublicCompletionToken(java.util.UUID.randomUUID());
            nextReminder.setNotificationSentAt(null);

            reminderRepository.save(nextReminder);
            return current;
        }

        return reminderRepository.save(current);
    }

    /**
     * Pauses a reminder: it stays in the active list but no e-mail is sent and a
     * recurring reminder is not rolled forward until it is resumed.
     */
    @Transactional
    public PersonalReminder pauseReminder(Long reminderId) {
        PersonalReminder reminder = findOrThrow(reminderId);
        if (reminder.isCompleted()) {
            throw new InvalidArgumentException("Ce rappel est déjà terminé, rien à mettre en pause.");
        }
        reminder.setPaused(true);
        return reminderRepository.save(reminder);
    }

    /** Resumes a paused reminder, using today's date in the application time zone. */
    @Transactional
    public PersonalReminder resumeReminder(Long reminderId) {
        return resumeReminder(reminderId, LocalDate.now(java.time.ZoneId.of("Africa/Douala")));
    }

    /**
     * Resumes a paused reminder. A recurring reminder whose due date went by while it
     * was paused jumps to its next future occurrence (no history entry is created for
     * the skipped ones) and is re-armed for notification.
     */
    @Transactional
    public PersonalReminder resumeReminder(Long reminderId, LocalDate today) {
        PersonalReminder reminder = findOrThrow(reminderId);
        if (!reminder.isPaused()) {
            return reminder;
        }
        reminder.setPaused(false);

        RepetitionType repetition = reminder.getRepetitionType();
        if (repetition != null && repetition != RepetitionType.NONE && reminder.getDueDate() != null) {
            LocalDate due = reminder.getDueDate();
            int safety = 0;
            while (due.isBefore(today) && safety++ < 1000) {
                LocalDate next = computeNextOccurrence(due, repetition);
                if (next == null) {
                    break;
                }
                due = next;
            }
            if (!due.equals(reminder.getDueDate())) {
                reminder.setDueDate(due);
                reminder.setNotificationSentAt(null);
            }
        }
        return reminderRepository.save(reminder);
    }

    private PersonalReminder findOrThrow(Long reminderId) {
        if (reminderId == null) {
            throw new InvalidArgumentException("L'identifiant du rappel est obligatoire.");
        }
        return reminderRepository.findById(reminderId)
                .orElseThrow(() -> new EntityNotFoundException("PersonalReminder", reminderId));
    }

    /**
     * Real deletion (hard delete). No soft-delete flag: the row is physically
     * removed from personal_reminders.
     */
    @Transactional
    public void deleteReminder(Long reminderId) {
        if (reminderId == null) {
            throw new InvalidArgumentException("L'identifiant du rappel est obligatoire.");
        }
        if (!reminderRepository.existsById(reminderId)) {
            throw new EntityNotFoundException("PersonalReminder", reminderId);
        }
        reminderRepository.deleteById(reminderId);
    }

    /**
     * Clears the user's history of completed reminders
     * (active reminders are left untouched).
     */
    @Transactional
    public void deleteHistoryForUser(Long userId) {
        reminderRepository.deleteByUser_IdAndCompletedTrue(userId);
    }

    /**
     * Variant of markAsCompleted() using the public token of the "mark as done"
     * link received by e-mail (see PublicReminderController). Idempotent: if the
     * reminder is already completed, nothing happens and the current state is returned.
     */
    @Transactional
    public PersonalReminder completeByPublicToken(java.util.UUID token) {
        PersonalReminder reminder = reminderRepository.findByPublicCompletionToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Lien invalide ou expiré."));
        if (reminder.isCompleted()) {
            return reminder;
        }
        return markAsCompleted(reminder.getId());
    }

    /**
     * Returns a reminder that strictly belongs to the current user. Prevents user A
     * from reading or modifying user B's reminder by simply guessing an identifier.
     */
    public PersonalReminder getOwnedReminder(Long reminderId, Long userId) {
        PersonalReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new EntityNotFoundException("PersonalReminder", reminderId));
        if (!reminder.getUser().getId().equals(userId)) {
            throw new EntityNotFoundException("PersonalReminder", reminderId);
        }
        return reminder;
    }

    /**
     * Lists all active (not completed) reminders of the user, ordered by due date.
     */
    public List<PersonalReminder> listActiveForUser(Long userId) {
        return reminderRepository.findByUser_IdOrderByDueDateAsc(userId).stream()
                .filter(r -> !r.isCompleted())
                .collect(Collectors.toList());
    }

    /**
     * History of the user's already completed reminders (/history endpoint).
     */
    public List<PersonalReminder> listHistoryForUser(Long userId) {
        return reminderRepository.findByUser_IdOrderByDueDateAsc(userId).stream()
                .filter(PersonalReminder::isCompleted)
                .collect(Collectors.toList());
    }

    /**
     * For recurring personal reminders whose date/time has passed and that are not
     * "done" yet: closes the current occurrence and generates the next one (same logic
     * as markAsCompleted), so a reminder without money does not wait for a "Done" click
     * to move on. Paused reminders are ignored.
     */
    @Transactional
    public int rollForwardOverdueRecurring(java.time.LocalDate today, java.time.LocalDateTime now) {
        java.util.List<PersonalReminder> active = reminderRepository.findAll().stream()
                .filter(r -> !r.isCompleted())
                .filter(r -> !r.isPaused())
                .filter(r -> r.getRepetitionType() != null && r.getRepetitionType() != RepetitionType.NONE)
                .filter(r -> r.getDueDate() != null && !r.getDueDate().isAfter(today))
                .toList();

        int rolled = 0;
        for (PersonalReminder start : active) {
            // Multi-occurrence catch-up: if 5 days of a DAILY reminder were missed, keep
            // going until a future due date (or today, not due yet).
            Long currentId = start.getId();
            int safety = 0;
            while (currentId != null && safety++ < 400) {
                PersonalReminder current = reminderRepository.findById(currentId).orElse(null);
                if (current == null || current.isCompleted() || current.isPaused()) {
                    break;
                }
                if (current.getRepetitionType() == null || current.getRepetitionType() == RepetitionType.NONE) {
                    break;
                }
                if (current.getDueDate() == null) {
                    break;
                }
                java.time.LocalTime time = current.getDueTime() != null
                        ? current.getDueTime() : java.time.LocalTime.of(8, 0);
                java.time.LocalDateTime trigger = current.getDueDate().atTime(time);
                // Not time yet (later today) -> stop
                if (now.isBefore(trigger)) {
                    break;
                }

                LocalDate nextDate = computeNextOccurrence(current.getDueDate(), current.getRepetitionType());
                markAsCompleted(current.getId());
                rolled++;

                if (nextDate == null) {
                    break;
                }
                // New occurrence created by markAsCompleted: find it again
                final LocalDate nd = nextDate;
                final Long userId = current.getUser().getId();
                final String title = current.getTitle();
                PersonalReminder next = reminderRepository.findAll().stream()
                        .filter(r -> !r.isCompleted())
                        .filter(r -> r.getUser() != null && userId.equals(r.getUser().getId()))
                        .filter(r -> title.equals(r.getTitle()))
                        .filter(r -> nd.equals(r.getDueDate()))
                        .findFirst()
                        .orElse(null);
                if (next == null) {
                    break;
                }
                currentId = next.getId();
            }
        }
        return rolled;
    }

    /** Computes the next occurrence date according to the recurrence type. */
    private LocalDate computeNextOccurrence(LocalDate currentDueDate, RepetitionType repetitionType) {
        if (currentDueDate == null) {
            throw new InvalidArgumentException("Impossible de calculer la prochaine occurrence : due_date est nulle.");
        }
        switch (repetitionType) {
            case DAILY:
                return currentDueDate.plusDays(1);
            case WEEKLY:
                return currentDueDate.plusWeeks(1);
            case MONTHLY:
                return currentDueDate.plusMonths(1);
            case YEARLY:
                return currentDueDate.plusYears(1);
            case NONE:
            default:
                return null;
        }
    }

    private void validateReminder(PersonalReminder reminder) {
        if (reminder == null) {
            throw new InvalidArgumentException("Le rappel ne peut pas être nul.");
        }
        if (reminder.getUser() == null || reminder.getUser().getId() == null) {
            throw new InvalidArgumentException("Le rappel doit être rattaché à un utilisateur valide.");
        }
        if (reminder.getTitle() == null || reminder.getTitle().isBlank()) {
            throw new InvalidArgumentException("Le titre du rappel est obligatoire.");
        }
        if (reminder.getDueDate() == null) {
            throw new InvalidArgumentException("La date d'échéance (due_date) est obligatoire.");
        }
        if (reminder.getRepetitionType() == null) {
            reminder.setRepetitionType(RepetitionType.NONE);
        }
    }
}
