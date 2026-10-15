-- V16 : jours de relance choisis, langue par membre, renvoi des e-mails échoués,
--       report de dette à la récurrence et paiement en ligne (sessions).

-- 1) Jours de relance AVANT l'échéance choisis par l'organisateur (ex. '3,1,0').
--    NULL = comportement historique (J-7, J-3, J0).
ALTER TABLE group_events
    ADD COLUMN IF NOT EXISTS reminder_days_before VARCHAR(40);

-- 2) Lien vers l'occurrence précédente (événements récurrents) et drapeau
--    "dette déjà reportée". La dette de l'occurrence précédente n'est reportée
--    qu'à la date de la nouvelle occurrence (pas avant).
ALTER TABLE group_events
    ADD COLUMN IF NOT EXISTS previous_event_id BIGINT REFERENCES group_events (id) ON DELETE SET NULL;
ALTER TABLE group_events
    ADD COLUMN IF NOT EXISTS carry_applied BOOLEAN NOT NULL DEFAULT TRUE;

-- 3) Langue des messages d'un membre (facultative : sinon langue du membre inscrit / du propriétaire).
ALTER TABLE group_members
    ADD COLUMN IF NOT EXISTS preferred_locale VARCHAR(5);

-- 4) Renvoi automatique des e-mails échoués.
ALTER TABLE notification_logs
    ADD COLUMN IF NOT EXISTS subject VARCHAR(255),
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_notification_logs_status ON notification_logs (status);

-- 5) Sessions de paiement en ligne (Faroty Pay).
CREATE TABLE IF NOT EXISTS payment_sessions (
    id                       BIGSERIAL PRIMARY KEY,
    session_token            VARCHAR(255) NOT NULL UNIQUE,
    payment_token_uuid       UUID,
    event_member_status_id   BIGINT NOT NULL REFERENCES event_member_status (id) ON DELETE CASCADE,
    amount                   NUMERIC(14, 2) NOT NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed                BOOLEAN NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_checked_at          TIMESTAMP WITH TIME ZONE,
    CONSTRAINT payment_sessions_status_check CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_payment_sessions_pending ON payment_sessions (status, processed);
