package com.echeo.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Jeton à usage unique permettant à un membre de régler un
 * EventMemberStatus (ex. lien de paiement envoyé par email/SMS).
 * Le jeton expire à expiresAt et ne peut être consommé qu'une fois (isUsed).
 */
@Entity
@Table(name = "payment_tokens")
public class PaymentToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "token_uuid", nullable = false, unique = true, updatable = false)
    private UUID tokenUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_member_status_id", nullable = false)
    @JsonIgnoreProperties({"paymentHistory", "paymentTokens", "event", "member"})
    private EventMemberStatus eventMemberStatus;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "is_used", nullable = false)
    private boolean used = false;

    public PaymentToken() {
    }

    public PaymentToken(Long id, UUID tokenUuid, EventMemberStatus eventMemberStatus,
                         OffsetDateTime expiresAt, boolean used) {
        this.id = id;
        this.tokenUuid = tokenUuid;
        this.eventMemberStatus = eventMemberStatus;
        this.expiresAt = expiresAt;
        this.used = used;
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (this.tokenUuid == null) {
            this.tokenUuid = UUID.randomUUID();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getTokenUuid() {
        return tokenUuid;
    }

    public void setTokenUuid(UUID tokenUuid) {
        this.tokenUuid = tokenUuid;
    }

    public EventMemberStatus getEventMemberStatus() {
        return eventMemberStatus;
    }

    public void setEventMemberStatus(EventMemberStatus eventMemberStatus) {
        this.eventMemberStatus = eventMemberStatus;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }
}
