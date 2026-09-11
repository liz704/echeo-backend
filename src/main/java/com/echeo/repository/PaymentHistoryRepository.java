package com.echeo.repository;

import com.echeo.model.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {

    List<PaymentHistory> findByEventMemberStatus_IdOrderByPaidAtDesc(Long eventMemberStatusId);
}
