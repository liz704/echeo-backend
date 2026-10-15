package com.echeo.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Session de paiement en ligne (Faroty Pay) liée à l'échéance d'un membre. */
@Entity
@Table(name = "payment_sessions")
public class PaymentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_token", nullable = false, unique = true)
    private String sessionToken;

    @Column(name = "payment_token_uuid")
    private UUID paymentTokenUuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_member_status_id", nullable = false)
    private EventMemberStatus eventMemberStatus;

    @Column(nullable = false)
    private BigDecimal amount;

    // PENDING / SUCCESS / FAILED
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    // true quand le versement a été enregistré (garantit l'idempotence)
    @Column(nullable = false)
    private boolean processed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
    public UUID getPaymentTokenUuid() { return paymentTokenUuid; }
    public void setPaymentTokenUuid(UUID paymentTokenUuid) { this.paymentTokenUuid = paymentTokenUuid; }
    public EventMemberStatus getEventMemberStatus() { return eventMemberStatus; }
    public void setEventMemberStatus(EventMemberStatus s) { this.eventMemberStatus = s; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(OffsetDateTime t) { this.lastCheckedAt = t; }
}
