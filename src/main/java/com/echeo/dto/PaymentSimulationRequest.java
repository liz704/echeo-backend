package com.echeo.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Requête de simulation de paiement — réservée aux profils dev/test (voir
 * PaymentSimulationService). Sert à tester le comportement du système de
 * paiement (PaymentService.recordPayment, via le même chemin que
 * PublicPaymentController) sans dépendre d'un vrai fournisseur Mobile Money.
 */
public class PaymentSimulationRequest {

    @NotNull(message = "Le token de paiement est obligatoire.")
    private UUID paymentToken;

    @NotNull(message = "Le statut simulé est obligatoire.")
    private SimulatedStatus simulatedStatus;

    @NotNull(message = "Le moyen de paiement est obligatoire.")
    private SimulatedPaymentMethod paymentMethod;

    @NotNull(message = "Le montant est obligatoire.")
    private BigDecimal amountPaid;

    public enum SimulatedStatus {
        SUCCESS, FAILED
    }

    // MOMO/OM/CARD plutôt que l'enum PaymentMethod existant (CASH/MOBILE_MONEY/CARD) :
    // ce DTO simule un futur webhook de provider réel, dont le vocabulaire
    // (MOMO = MTN Mobile Money, OM = Orange Money) diffère volontairement du
    // nôtre. PaymentSimulationService fait la conversion vers PaymentMethod.
    public enum SimulatedPaymentMethod {
        MOMO, OM, CARD
    }

    public UUID getPaymentToken() {
        return paymentToken;
    }

    public void setPaymentToken(UUID paymentToken) {
        this.paymentToken = paymentToken;
    }

    public SimulatedStatus getSimulatedStatus() {
        return simulatedStatus;
    }

    public void setSimulatedStatus(SimulatedStatus simulatedStatus) {
        this.simulatedStatus = simulatedStatus;
    }

    public SimulatedPaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(SimulatedPaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }
}
