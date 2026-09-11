package com.echeo.repository;

import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventMemberStatusRepository extends JpaRepository<EventMemberStatus, Long> {

    // Récupère les membres encore redevables (non soldés) pour un événement donné,
    // utilisé par le scheduler pour cibler uniquement ceux à relancer.
    List<EventMemberStatus> findByEvent_IdAndStatusIn(Long eventId, List<PaymentStatus> statuses);

    // Tous les statuts d'un événement, peu importe leur état — utilisé lors
    // de la génération de l'occurrence suivante (report du solde).
    List<EventMemberStatus> findByEvent_Id(Long eventId);

    // "Mes cotisations" : toutes les échéances (tous groupes confondus) où
    // l'utilisateur courant EST le membre concerné (via son GroupMember lié).
    // Un membre externe (sans compte) n'a pas d'utilisateur courant, donc
    // n'est jamais concerné par cette requête — c'est attendu.
    List<EventMemberStatus> findByGroupMember_User_IdOrderByIdDesc(Long userId);
}
