package com.echeo.dto;

import com.echeo.model.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Entrée aplatie de l'historique des paiements d'un groupe
 * (évite les soucis de sérialisation JSON des entités lazy).
 */
public class GroupPaymentHistoryItem {

    private Long id;
    private BigDecimal amountPaid;
    private PaymentMethod paymentMethod;
    private String transactionRef;
    private OffsetDateTime paidAt;
    private Long eventId;
    private String eventTitle;
    private Long eventMemberStatusId;
    private String memberFullName;

    public GroupPaymentHistoryItem() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public Long getEventMemberStatusId() {
        return eventMemberStatusId;
    }

    public void setEventMemberStatusId(Long eventMemberStatusId) {
        this.eventMemberStatusId = eventMemberStatusId;
    }

    public String getMemberFullName() {
        return memberFullName;
    }

    public void setMemberFullName(String memberFullName) {
        this.memberFullName = memberFullName;
    }
}
