package com.echeo.repository;

import com.echeo.model.entity.GroupEvent;
import com.echeo.model.enums.RepetitionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface GroupEventRepository extends JpaRepository<GroupEvent, Long> {

    // Point d'entrée du scheduler : tous les événements dont l'échéance
    // tombe exactement sur la date scrutée (J-7, J-3, J0).
    List<GroupEvent> findByEventDate(LocalDate eventDate);

    List<GroupEvent> findByGroup_IdOrderByEventDateAsc(Long groupId);

    // Événements récurrents dont l'échéance est passée ou atteinte, pas en
    // pause, et pour lesquels l'occurrence suivante n'a pas encore été créée.
    List<GroupEvent> findByRepetitionTypeNotAndPausedFalseAndNextOccurrenceIsNullAndEventDateLessThanEqual(
            RepetitionType repetitionType, LocalDate date);

    // Événements dont l'échéance est dépassée, pour le job de relances en
    // rafale (J+1, J+3, J+7 après échéance) et le passage en OVERDUE.
    List<GroupEvent> findByEventDateBefore(LocalDate date);
}
