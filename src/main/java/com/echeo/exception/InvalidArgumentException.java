package com.echeo.exception;

/**
 * Levée lorsqu'un argument métier est invalide (montant négatif ou nul,
 * token de paiement expiré/déjà utilisé, etc.).
 * À mapper vers un HTTP 400 dans le futur @ControllerAdvice (Étape 3).
 */
public class InvalidArgumentException extends RuntimeException {

    public InvalidArgumentException(String message) {
        super(message);
    }
}
