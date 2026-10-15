package com.echeo.repository;

import com.echeo.model.entity.PaymentSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentSessionRepository extends JpaRepository<PaymentSession, Long> {

    Optional<PaymentSession> findBySessionToken(String sessionToken);

    List<PaymentSession> findByPaymentTokenUuidAndStatusAndProcessedFalse(UUID paymentTokenUuid, String status);

    List<PaymentSession> findByStatusAndProcessedFalseAndCreatedAtAfter(String status, OffsetDateTime after);
}
