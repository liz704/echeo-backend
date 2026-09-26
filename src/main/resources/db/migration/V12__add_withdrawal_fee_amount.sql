-- Colonne manquante si une ancienne V10 (sans withdrawal_fee) avait déjà
-- été appliquée en base. IF NOT EXISTS pour rester idempotent.
ALTER TABLE group_events
    ADD COLUMN IF NOT EXISTS withdrawal_fee_amount NUMERIC(14, 2);
