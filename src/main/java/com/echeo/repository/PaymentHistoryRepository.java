package com.echeo.repository;

import com.echeo.model.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {

    List<PaymentHistory> findByEventMemberStatus_IdOrderByPaidAtDesc(Long eventMemberStatusId);

    /**
     * Tous les versements enregistrés pour les événements d'un groupe,
     * du plus récent au plus ancien — historique de groupe.
     */
    @Query("""
        SELECT h FROM PaymentHistory h
        JOIN FETCH h.eventMemberStatus s
        JOIN FETCH s.groupMember gm
        LEFT JOIN FETCH gm.user
        JOIN FETCH s.event e
        WHERE e.group.id = :groupId
        ORDER BY h.paidAt DESC
        """)
    List<PaymentHistory> findByGroupIdOrderByPaidAtDesc(@Param("groupId") Long groupId);

    /**
     * Tous les versements d'un événement donné, du plus récent au plus ancien.
     */
    @Query("""
        SELECT h FROM PaymentHistory h
        JOIN FETCH h.eventMemberStatus s
        JOIN FETCH s.groupMember gm
        LEFT JOIN FETCH gm.user
        WHERE s.event.id = :eventId
        ORDER BY h.paidAt DESC
        """)
    List<PaymentHistory> findByEventIdOrderByPaidAtDesc(@Param("eventId") Long eventId);
}

