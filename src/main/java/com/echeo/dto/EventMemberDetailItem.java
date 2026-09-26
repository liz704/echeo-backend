package com.echeo.dto;

import com.echeo.model.enums.PaymentMethod;
import com.echeo.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Détail du suivi d'un membre pour un événement donné :
 * statut courant + historique des versements (ou accusé de lecture).
 */
public class EventMemberDetailItem {

    private Long eventMemberStatusId;
    private Long groupMemberId;
    private String memberFullName;
    private String memberEmail;
    private PaymentStatus status;
    private BigDecimal requiredAmount;
    private BigDecimal paidAmount;
    private OffsetDateTime seenAt;
    private List<PaymentEntry> payments = new ArrayList<>();

    public static class PaymentEntry {
        private Long id;
        private BigDecimal amountPaid;
        private PaymentMethod paymentMethod;
        private String transactionRef;
        private OffsetDateTime paidAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public BigDecimal getAmountPaid() { return amountPaid; }
        public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }
        public PaymentMethod getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getTransactionRef() { return transactionRef; }
        public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
        public OffsetDateTime getPaidAt() { return paidAt; }
        public void setPaidAt(OffsetDateTime paidAt) { this.paidAt = paidAt; }
    }

    public Long getEventMemberStatusId() { return eventMemberStatusId; }
    public void setEventMemberStatusId(Long eventMemberStatusId) { this.eventMemberStatusId = eventMemberStatusId; }
    public Long getGroupMemberId() { return groupMemberId; }
    public void setGroupMemberId(Long groupMemberId) { this.groupMemberId = groupMemberId; }
    public String getMemberFullName() { return memberFullName; }
    public void setMemberFullName(String memberFullName) { this.memberFullName = memberFullName; }
    public String getMemberEmail() { return memberEmail; }
    public void setMemberEmail(String memberEmail) { this.memberEmail = memberEmail; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public BigDecimal getRequiredAmount() { return requiredAmount; }
    public void setRequiredAmount(BigDecimal requiredAmount) { this.requiredAmount = requiredAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public OffsetDateTime getSeenAt() { return seenAt; }
    public void setSeenAt(OffsetDateTime seenAt) { this.seenAt = seenAt; }
    public List<PaymentEntry> getPayments() { return payments; }
    public void setPayments(List<PaymentEntry> payments) { this.payments = payments; }
}
