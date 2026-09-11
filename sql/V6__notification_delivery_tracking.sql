-- ============================================================================
-- ÉCHÉO - Migration V6 : suivi de livraison réel des notifications
--
-- provider_message_id permet de faire le lien entre une notification envoyée
-- et l'événement de livraison renvoyé par le webhook du fournisseur d'email
-- (Brevo). Sans cet identifiant, impossible de savoir avec certitude si un
-- email a été réellement reçu (SENT ne veut dire que "accepté par le serveur
-- SMTP", pas "arrivé dans la boîte du destinataire").
-- ============================================================================

ALTER TABLE notification_logs
    DROP CONSTRAINT IF EXISTS notification_logs_status_check;

ALTER TABLE notification_logs
    ADD CONSTRAINT notification_logs_status_check
        CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'BOUNCED', 'FAILED'));

ALTER TABLE notification_logs
    ADD COLUMN provider_message_id VARCHAR(255);

CREATE INDEX idx_notification_logs_provider_message_id
    ON notification_logs (provider_message_id);
