package com.echeo.repository;

import com.echeo.model.entity.PersonalReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PersonalReminderRepository extends JpaRepository<PersonalReminder, Long> {

    List<PersonalReminder> findByUser_IdOrderByDueDateAsc(Long userId);

    // Utile pour un futur module de relance des rappels personnels
    // (même logique de cron que les paiements, sur due_date au lieu de event_date).
    List<PersonalReminder> findByDueDateAndCompletedFalse(LocalDate dueDate);
}
