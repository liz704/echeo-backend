-- ============================================================================
-- ÉCHÉO - Migration V9 : lien "marquer fait" dans l'email de rappel personnel
--
-- Permet de compléter un rappel personnel directement depuis un clic dans
-- l'email, sans repasser par l'application (voir PublicReminderController).
-- Jeton généré une fois à la création du rappel, réutilisé à chaque email
-- tant que le rappel n'est pas complété (pas besoin d'un jeton par envoi,
-- contrairement au paiement : marquer "fait" est une action idempotente et
-- sans enjeu financier).
-- ============================================================================

ALTER TABLE personal_reminders
    ADD COLUMN public_completion_token UUID UNIQUE;

CREATE INDEX idx_personal_reminders_completion_token ON personal_reminders (public_completion_token);
