package com.echeo.exception;

/**
 * Levée lorsqu'une entité recherchée par identifiant n'existe pas en base.
 * À mapper vers un HTTP 404 dans le futur @ControllerAdvice (Étape 3).
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String entityName, Long id) {
        super(entityName + " introuvable pour l'identifiant : " + id);
    }
}
