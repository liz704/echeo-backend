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
 * Logique métier des rappels personnels : création, modification,
 * complétion avec gestion de la récurrence, et suppression réelle (hard-delete).
 */
@Service
public class ReminderService {

    private final PersonalReminderRepository reminderRepository;

    public ReminderService(PersonalReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    /**
     * Crée un nouveau rappel personnel après validation métier de base.
     */
    @Transactional
    public PersonalReminder createReminder(PersonalReminder reminder) {
        validateReminder(reminder);
        reminder.setId(null);
        reminder.setCompleted(false);
        reminder.setNextOccurrence(null);
        reminder.setNotificationSentAt(null);
        reminder.setPublicCompletionToken(java.util.UUID.randomUUID());
        return reminderRepository.save(reminder);
    }

    /**
     * Met à jour un rappel existant (titre, description, date/heure, récurrence).
     * Ne modifie jamais l'utilisateur propriétaire, ni le statut is_completed
     * (voir markAsCompleted pour ce dernier).
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

        // Si la date/heure change, on ré-arme le rappel pour qu'il puisse
        // être renotifié au nouveau moment, même s'il avait déjà été envoyé.
        if (scheduleChanged) {
            existing.setNotificationSentAt(null);
        }

        return reminderRepository.save(existing);
    }

    /**
     * Marque un rappel comme terminé. Si le rappel est récurrent (repetitionType != NONE),
     * calcule automatiquement la prochaine occurrence, la persiste comme nouveau rappel actif,
     * et trace la date calculée sur le rappel courant (next_occurrence) à titre d'historique.
     *
     * @return le rappel courant mis à jour (complété). La nouvelle occurrence, si créée,
     *         est persistée en base mais n'est pas retournée par cette méthode.
     */
    @Transactional
    public PersonalReminder markAsCompleted(Long reminderId) {
        if (reminderId == null) {
            throw new InvalidArgumentException("L'identifiant du rappel est obligatoire.");
        }

        PersonalReminder current = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new EntityNotFoundException("PersonalReminder", reminderId));

        // Idempotent : un second "marquer fait" (double-clic app ou lien email)
        // ne doit pas régénérer une occurrence récurrente.
        if (current.isCompleted()) {
            return current;
        }

        current.setCompleted(true);

        RepetitionType repetition = current.getRepetitionType();
        if (repetition != null && repetition != RepetitionType.NONE) {
            LocalDate nextDate = computeNextOccurrence(current.getDueDate(), repetition);

            // Historique : on trace sur le rappel clôturé la date de la prochaine échéance générée.
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
     * Suppression réelle (hard-delete) en base. Aucune soft-delete/flag :
     * la ligne disparaît physiquement de personal_reminders.
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
     * Vide l'historique des rappels terminés de l'utilisateur
     * (ne touche pas aux rappels actifs).
     */
    @Transactional
    public void deleteHistoryForUser(Long userId) {
        reminderRepository.deleteByUser_IdAndCompletedTrue(userId);
    }

    /**
     * Récupère un rappel appartenant strictement à l'utilisateur courant.
     * Empêche un utilisateur A de lire/modifier le rappel d'un utilisateur B
     * via une simple devinette d'identifiant.
     */
    /**
     * Variante de markAsCompleted() via le jeton public du lien "marquer
     * fait" reçu par email (voir PublicReminderController). Idempotent : si
     * déjà complété, ne fait rien et renvoie l'état actuel.
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

    public PersonalReminder getOwnedReminder(Long reminderId, Long userId) {
        PersonalReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new EntityNotFoundException("PersonalReminder", reminderId));
        if (!reminder.getUser().getId().equals(userId)) {
            throw new EntityNotFoundException("PersonalReminder", reminderId);
        }
        return reminder;
    }

    /**
     * Liste tous les rappels actifs (non complétés) de l'utilisateur, triés par échéance.
     */
    public List<PersonalReminder> listActiveForUser(Long userId) {
        return reminderRepository.findByUser_IdOrderByDueDateAsc(userId).stream()
                .filter(r -> !r.isCompleted())
                .collect(Collectors.toList());
    }

    /**
     * Historique des rappels déjà complétés pour l'utilisateur (endpoint /history).
     */
    public List<PersonalReminder> listHistoryForUser(Long userId) {
        return reminderRepository.findByUser_IdOrderByDueDateAsc(userId).stream()
                .filter(PersonalReminder::isCompleted)
                .collect(Collectors.toList());
    }

    /**
     * Calcule la date de la prochaine occurrence en fonction du type de récurrence.
     */

    /**
     * Pour les rappels personnels récurrents dont la date/heure est dépassée
     * et qui ne sont pas encore "faits" : clôture l'occurrence courante et
     * génère la suivante (même logique que markAsCompleted). Ainsi un rappel
     * sans argent n'attend pas un clic "Fait" pour avancer.
     */
    @Transactional
    public int rollForwardOverdueRecurring(java.time.LocalDate today, java.time.LocalDateTime now) {
        java.util.List<PersonalReminder> active = reminderRepository.findAll().stream()
                .filter(r -> !r.isCompleted())
                .filter(r -> r.getRepetitionType() != null && r.getRepetitionType() != RepetitionType.NONE)
                .filter(r -> r.getDueDate() != null && !r.getDueDate().isAfter(today))
                .toList();

        int rolled = 0;
        for (PersonalReminder start : active) {
            // Rattrapage multi-occurrences : si 5 jours de DAILY manqués, on
            // enchaîne jusqu'à une échéance future (ou aujourd'hui pas encore due).
            Long currentId = start.getId();
            int safety = 0;
            while (currentId != null && safety++ < 400) {
                PersonalReminder current = reminderRepository.findById(currentId).orElse(null);
                if (current == null || current.isCompleted()) {
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
                // Pas encore l'heure (aujourd'hui plus tard) → stop
                if (now.isBefore(trigger)) {
                    break;
                }

                LocalDate nextDate = computeNextOccurrence(current.getDueDate(), current.getRepetitionType());
                markAsCompleted(current.getId());
                rolled++;

                if (nextDate == null) {
                    break;
                }
                // Nouvelle occurrence créée par markAsCompleted : on la retrouve
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
