package com.echeo.util;

import com.echeo.model.enums.PaymentStatus;

import java.math.BigDecimal;

/**
 * Règles de calcul des montants (classe pure, sans dépendance Spring, testable seule).
 *
 * Principe : le montant saisi à la création est le montant que CHAQUE membre doit
 * (il n'est jamais divisé). Les frais de retrait s'y ajoutent pour un paiement en ligne :
 * montant à payer = montant demandé + frais.
 * Un membre est considéré comme "à jour" (PAID) dès qu'il a versé le montant demandé
 * (espèces : pas de frais) ; au-delà de montant + frais, c'est un SURPLUS.
 */
public final class PaymentRules {

    private PaymentRules() {
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Montant total à payer en ligne : montant demandé + frais de retrait. */
    public static BigDecimal amountDue(BigDecimal required, BigDecimal fee) {
        return nz(required).add(nz(fee));
    }

    public static PaymentStatus statusFor(BigDecimal paid, BigDecimal required, BigDecimal fee) {
        BigDecimal p = nz(paid);
        BigDecimal r = nz(required);
        if (p.compareTo(r) < 0) {
            return PaymentStatus.PARTIALLY_PAID;
        }
        if (p.compareTo(amountDue(r, fee)) <= 0) {
            return PaymentStatus.PAID;
        }
        return PaymentStatus.SURPLUS;
    }

    /** Surplus au-delà de montant + frais (0 sinon). */
    public static BigDecimal surplus(BigDecimal paid, BigDecimal required, BigDecimal fee) {
        BigDecimal s = nz(paid).subtract(amountDue(required, fee));
        return s.signum() > 0 ? s : BigDecimal.ZERO;
    }

    /**
     * Reste à payer en ligne (frais inclus). 0 dès que le membre a versé le montant demandé.
     * Si rien n'est dû de la part du membre (montant 0) : 0.
     */
    public static BigDecimal remainingToPay(BigDecimal paid, BigDecimal required, BigDecimal fee) {
        BigDecimal r = nz(required);
        if (r.signum() <= 0 || nz(paid).compareTo(r) >= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal rest = amountDue(r, fee).subtract(nz(paid));
        return rest.signum() > 0 ? rest : BigDecimal.ZERO;
    }

    /** Reste hors frais (espèces) : montant demandé − déjà payé, jamais négatif. */
    public static BigDecimal remainingCash(BigDecimal paid, BigDecimal required) {
        BigDecimal rest = nz(required).subtract(nz(paid));
        return rest.signum() > 0 ? rest : BigDecimal.ZERO;
    }

    /** Un membre a "tout payé" quand PAID ou SURPLUS. */
    public static boolean isSettled(PaymentStatus s) {
        return s == PaymentStatus.PAID || s == PaymentStatus.SURPLUS;
    }
}
