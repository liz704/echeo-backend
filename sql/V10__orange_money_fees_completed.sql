-- ============================================================================
-- ÉCHÉO - Migration V10
-- - Moyen de paiement ORANGE_MONEY
-- - Frais de retrait (withdrawal_fees) sur chaque versement
-- - Marquage "terminé" des événements de groupe (historique + récurrence)
-- ============================================================================

-- 1. payment_method : ajouter ORANGE_MONEY
ALTER TABLE payment_history
    DROP CONSTRAINT IF EXISTS payment_history_payment_method_check;

ALTER TABLE payment_history
    ADD CONSTRAINT payment_history_payment_method_check
        CHECK (payment_method IN ('CASH', 'MOBILE_MONEY', 'ORANGE_MONEY', 'CARD'));

-- 2. Frais de retrait optionnels par versement
ALTER TABLE payment_history
    ADD COLUMN IF NOT EXISTS withdrawal_fees NUMERIC(14, 2) NOT NULL DEFAULT 0
        CHECK (withdrawal_fees >= 0);

-- 3. Événement terminé (tous les membres soldés / ont vu) → historique
ALTER TABLE group_events
    ADD COLUMN IF NOT EXISTS completed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE group_events
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_group_events_completed
    ON group_events (completed, event_date);
