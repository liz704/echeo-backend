-- ============================================================================
-- ÉCHÉO - Migration V10 : Orange Money + tolérance "frais de retrait"
--
-- 1. Ajout d'ORANGE_MONEY comme moyen de paiement distinct de MOBILE_MONEY.
-- 2. group_events.withdrawal_fee_amount : montant optionnel (frais de
--    retrait anticipés) que le propriétaire peut définir à la création
--    d'une cotisation. Un membre qui envoie required_amount + ces frais
--    n'est plus compté en SURPLUS pour cette marge (voir PaymentService).
-- ============================================================================

ALTER TABLE payment_history
    DROP CONSTRAINT IF EXISTS payment_history_payment_method_check;

ALTER TABLE payment_history
    ADD CONSTRAINT payment_history_payment_method_check
        CHECK (payment_method IN ('CASH', 'MOBILE_MONEY', 'ORANGE_MONEY', 'CARD'));

ALTER TABLE group_events
    ADD COLUMN withdrawal_fee_amount NUMERIC(14, 2);
