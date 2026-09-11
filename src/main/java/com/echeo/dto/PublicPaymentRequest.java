package com.echeo.dto;

import com.echeo.model.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Requête de paiement via le lien public (sans authentification JWT) :
 * le token dans l'URL fait office d'autorisation.
 */
public class PublicPaymentRequest {

    @NotNull(message = "Le montant versé est obligatoire.")
    @DecimalMin(value = "0.01", message = "Le montant versé doit être strictement positif.")
    private BigDecimal amount;

    @NotNull(message = "Le moyen de paiement est obligatoire.")
    private PaymentMethod paymentMethod;

    private String transactionRef;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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
}
