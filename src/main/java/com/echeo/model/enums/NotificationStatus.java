package com.echeo.model.enums;

/**
 * Statut d'envoi ET de livraison d'une notification.
 * PENDING  : pas encore traitée.
 * SENT     : acceptée par le serveur d'envoi (ne garantit PAS la réception).
 * DELIVERED: confirmée reçue par le fournisseur (webhook Brevo).
 * BOUNCED  : rejetée par le serveur destinataire (adresse invalide, etc.).
 * FAILED   : échec technique avant même l'envoi.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    DELIVERED,
    BOUNCED,
    FAILED
}
