package com.echeo.repository;

import com.echeo.model.entity.PaymentToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTokenRepository extends JpaRepository<PaymentToken, Long> {

    Optional<PaymentToken> findByTokenUuid(UUID tokenUuid);
}
