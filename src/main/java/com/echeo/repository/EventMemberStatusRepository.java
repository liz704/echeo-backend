package com.echeo.repository;

import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventMemberStatusRepository extends JpaRepository<EventMemberStatus, Long> {

    // Récupère les membres encore redevables (non soldés) pour un événement donné,
    // utilisé par le scheduler pour cibler uniquement ceux à relancer.
    @Query("""
        SELECT DISTINCT s FROM EventMemberStatus s
        JOIN FETCH s.groupMember gm
        LEFT JOIN FETCH gm.user
        JOIN FETCH s.event
        WHERE s.event.id = :eventId
          AND s.status IN :statuses
        """)
    List<EventMemberStatus> findByEvent_IdAndStatusIn(
            @Param("eventId") Long eventId,
            @Param("statuses") List<PaymentStatus> statuses);

    // Tous les statuts d'un événement, peu importe leur état — utilisé lors
    // de la génération de l'occurrence suivante (report du solde).
    List<EventMemberStatus> findByEvent_Id(Long eventId);

    // "Mes cotisations" : toutes les échéances (tous groupes confondus) où
    // l'utilisateur courant EST le membre concerné (via son GroupMember lié).
    // Un membre externe (sans compte) n'a pas d'utilisateur courant, donc
    // n'est jamais concerné par cette requête — c'est attendu.
    List<EventMemberStatus> findByGroupMember_User_IdOrderByIdDesc(Long userId);
}