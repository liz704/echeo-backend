package com.echeo.repository;

import com.echeo.model.entity.GroupEvent;
import com.echeo.model.enums.RepetitionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GroupEventRepository extends JpaRepository<GroupEvent, Long> {

    @Query("SELECT e FROM GroupEvent e JOIN FETCH e.group WHERE e.id = :id")
    Optional<GroupEvent> findByIdWithGroup(@Param("id") Long id);

    // Point d'entrée du scheduler : tous les événements dont l'échéance
    // tombe exactement sur la date scrutée (J-7, J-3, J0).
    List<GroupEvent> findByEventDate(LocalDate eventDate);

    List<GroupEvent> findByGroup_IdOrderByEventDateAsc(Long groupId);

    /** Événements passés du groupe (historique), du plus récent au plus ancien. */
    List<GroupEvent> findByGroup_IdAndEventDateBeforeOrderByEventDateDesc(Long groupId, LocalDate date);

    /** Événements à venir (ou du jour), du plus proche au plus lointain. */
    List<GroupEvent> findByGroup_IdAndEventDateGreaterThanEqualOrderByEventDateAsc(Long groupId, LocalDate date);

    // Événements récurrents dont l'échéance est passée ou atteinte, pas en
    // pause, et pour lesquels l'occurrence suivante n'a pas encore été créée.
    List<GroupEvent> findByRepetitionTypeNotAndPausedFalseAndNextOccurrenceIsNullAndEventDateLessThanEqual(
            RepetitionType repetitionType, LocalDate date);

    // Événements dont l'échéance est dépassée, pour le job de relances en
    // rafale (J+1, J+3, J+7 après échéance) et le passage en OVERDUE.
    List<GroupEvent> findByEventDateBefore(LocalDate date);
}