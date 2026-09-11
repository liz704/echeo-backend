package com.echeo.dto;

import com.echeo.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Vue publique (sans données sensibles) d'une échéance accessible via un
 * token de paiement : ce qui est affiché sur la page de paiement publique.
 */
public class PublicPaymentDetailsResponse {

    private String eventTitle;
    private String memberFullName;
    private BigDecimal requiredAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private PaymentStatus status;
    private OffsetDateTime tokenExpiresAt;

    public PublicPaymentDetailsResponse() {
    }

    public PublicPaymentDetailsResponse(String eventTitle, String memberFullName, BigDecimal requiredAmount,
                                         BigDecimal paidAmount, BigDecimal remainingAmount, PaymentStatus status,
                                         OffsetDateTime tokenExpiresAt) {
        this.eventTitle = eventTitle;
        this.memberFullName = memberFullName;
        this.requiredAmount = requiredAmount;
        this.paidAmount = paidAmount;
        this.remainingAmount = remainingAmount;
        this.status = status;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public String getMemberFullName() {
        return memberFullName;
    }

    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public OffsetDateTime getTokenExpiresAt() {
        return tokenExpiresAt;
    }
}
