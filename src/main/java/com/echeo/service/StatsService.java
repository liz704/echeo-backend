package com.echeo.service;

import com.echeo.dto.StatsResponse;
import com.echeo.model.entity.EventMemberStatus;
import com.echeo.repository.EventMemberStatusRepository;
import com.echeo.repository.PersonalReminderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calcule les statistiques globales de la plateforme : rappels et paiements.
 * NOTE PERFORMANCE : les agrégats sont calculés en mémoire via findAll() pour
 * rester simple et lisible à ce stade. Sur un volume de données important,
 * remplacer par des requêtes JPQL/native SUM/COUNT pour éviter de charger
 * toutes les lignes en mémoire.
 */
@Service
public class StatsService {

    private final PersonalReminderRepository reminderRepository;
    private final EventMemberStatusRepository eventMemberStatusRepository;

    public StatsService(PersonalReminderRepository reminderRepository,
                         EventMemberStatusRepository eventMemberStatusRepository) {
        this.reminderRepository = reminderRepository;
        this.eventMemberStatusRepository = eventMemberStatusRepository;
    }

    public StatsResponse getGlobalStats() {
        long totalReminders = reminderRepository.count();
        long completedReminders = reminderRepository.findAll().stream()
                .filter(r -> r.isCompleted())
                .count();
        long pendingReminders = totalReminders - completedReminders;

        List<EventMemberStatus> allStatuses = eventMemberStatusRepository.findAll();

        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal totalRemaining = BigDecimal.ZERO;
        BigDecimal totalSurplus = BigDecimal.ZERO;

        for (EventMemberStatus status : allStatuses) {
            BigDecimal required = status.getRequiredAmount() != null ? status.getRequiredAmount() : BigDecimal.ZERO;
            BigDecimal paid = status.getPaidAmount() != null ? status.getPaidAmount() : BigDecimal.ZERO;

            totalExpected = totalExpected.add(required);
            totalCollected = totalCollected.add(paid);

            BigDecimal diff = required.subtract(paid);
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                totalRemaining = totalRemaining.add(diff);
            } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                totalSurplus = totalSurplus.add(diff.negate());
            }
        }

        return new StatsResponse(
                totalReminders, completedReminders, pendingReminders,
                totalExpected, totalCollected, totalRemaining, totalSurplus
        );
    }
}
