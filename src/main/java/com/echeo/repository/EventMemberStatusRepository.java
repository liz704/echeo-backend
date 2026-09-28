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

    // Version "affichage" : charge groupMember (+ user) et event pour que
    // l'UI puisse afficher nom + titre + montant sans lazy-load hors session.
    @Query("""
        SELECT DISTINCT s FROM EventMemberStatus s
        JOIN FETCH s.groupMember gm
        LEFT JOIN FETCH gm.user
        JOIN FETCH s.event
        WHERE s.event.id = :eventId
        """)
    List<EventMemberStatus> findByEvent_IdWithDetails(@Param("eventId") Long eventId);

    // "Mes cotisations" : toutes les échéances (tous groupes confondus) où
    // l'utilisateur courant EST le membre concerné (via son GroupMember lié).
    // Un membre externe (sans compte) n'a pas d'utilisateur courant, donc
    // n'est jamais concerné par cette requête — c'est attendu.
    @Query("""
        SELECT DISTINCT s FROM EventMemberStatus s
        JOIN FETCH s.groupMember gm
        LEFT JOIN FETCH gm.user
        JOIN FETCH s.event e
        JOIN FETCH e.group
        WHERE gm.user.id = :userId
        """)
    List<EventMemberStatus> findByGroupMember_User_IdWithDetails(@Param("userId") Long userId);

    /**
     * Échéances gérées par le propriétaire : tous les statuts des événements
     * des groupes dont l'utilisateur est owner. Permet à la page Paiements
     * d'afficher quelque chose même quand les membres sont externes.
     */
    @Query("""
        SELECT DISTINCT s FROM EventMemberStatus s
        JOIN FETCH s.groupMember gm
        LEFT JOIN FETCH gm.user
        JOIN FETCH s.event e
        JOIN e.group g
        WHERE g.owner.id = :ownerId
        """)
    List<EventMemberStatus> findManagedByOwnerId(@Param("ownerId") Long ownerId);

    // Utilisé lors du retrait d'un membre du groupe : purge ses statuts de
    // paiement avant de le supprimer lui-même (voir GroupService.removeMember).
    void deleteByGroupMember_Id(Long groupMemberId);

    // Résolution du lien public "marquer comme vu" (événements sans argent) —
    // voir PublicAcknowledgmentController.
    java.util.Optional<EventMemberStatus> findByPublicAckToken(java.util.UUID publicAckToken);
}