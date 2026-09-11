package com.echeo.model.entity;

import com.echeo.model.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Trace immuable d'un versement effectué par un membre pour un
 * EventMemberStatus donné. Permet de reconstituer l'historique
 * complet des paiements (partiels, complets, surplus successifs).
 */
@Entity
@Table(name = "payment_history")
public class PaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_member_status_id", nullable = false)
    @JsonIgnoreProperties({"paymentHistory", "paymentTokens", "event", "member"})
    private EventMemberStatus eventMemberStatus;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "transaction_ref", length = 255)
    private String transactionRef;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private OffsetDateTime paidAt;

    public PaymentHistory() {
    }

    public PaymentHistory(Long id, EventMemberStatus eventMemberStatus, BigDecimal amountPaid,
                           PaymentMethod paymentMethod, String transactionRef, OffsetDateTime paidAt) {
        this.id = id;
        this.eventMemberStatus = eventMemberStatus;
        this.amountPaid = amountPaid;
        this.paymentMethod = paymentMethod;
        this.transactionRef = transactionRef;
        this.paidAt = paidAt;
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (this.paidAt == null) {
            this.paidAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EventMemberStatus getEventMemberStatus() {
        return eventMemberStatus;
    }

    public void setEventMemberStatus(EventMemberStatus eventMemberStatus) {
        this.eventMemberStatus = eventMemberStatus;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(OffsetDateTime paidAt) {
        this.paidAt = paidAt;
    }
}
