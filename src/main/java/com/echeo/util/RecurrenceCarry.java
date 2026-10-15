package com.echeo.util;

import java.math.BigDecimal;

/** Report de solde d'une occurrence à la suivante (classe pure). */
public final class RecurrenceCarry {

    private RecurrenceCarry() {
    }

    /**
     * Nouveau montant requis = montant de base + dette restante de l'occurrence précédente
     * (superposition), ou − surplus déjà versé (jamais sous 0).
     * Dette = required − paid de l'occurrence précédente (frais exclus).
     */
    public static BigDecimal nextRequired(BigDecimal base, BigDecimal prevRequired, BigDecimal prevPaid) {
        BigDecimal debt = PaymentRules.nz(prevRequired).subtract(PaymentRules.nz(prevPaid));
        BigDecimal result = PaymentRules.nz(base).add(debt);
        return result.signum() < 0 ? BigDecimal.ZERO : result;
    }
}
