-- ============================================================================
-- ÉCHÉO - Migration V8 : rappels de groupe SANS argent (accusé de réception)
--
-- Jusqu'ici, tout GroupEvent exigeait un montant cible (target_amount) et
-- tout EventMemberStatus un montant requis (required_amount) : impossible
-- de créer un simple rappel de groupe informatif (pas de cotisation).
--
-- Comportement :
-- - target_amount / required_amount / paid_amount deviennent optionnels.
--   NULL = événement/statut "sans argent" (simple information à diffuser).
-- - Le statut d'un membre pour un événement sans argent suit désormais
--   NOT_SEEN -> SEEN (accusé de lecture), ou OVERDUE si l'échéance est
--   passée sans qu'aucun accusé n'ait été enregistré (même logique de
--   retard que pour l'argent, réutilise le statut OVERDUE existant).
-- - public_ack_token : jeton public permettant à un membre externe (sans
--   compte ÉCHÉO) de marquer le rappel comme vu via un simple lien reçu
--   par email, sans étape de confirmation supplémentaire (contrairement au
--   paiement, l'enjeu est faible : la visite du lien suffit).
-- - seen_at : horodatage du "vu", NULL tant que non consulté.
-- ============================================================================

ALTER TABLE group_events
    ALTER COLUMN target_amount DROP NOT NULL;

ALTER TABLE event_member_status
    ALTER COLUMN required_amount DROP NOT NULL;

ALTER TABLE event_member_status
    ALTER COLUMN paid_amount DROP NOT NULL;

ALTER TABLE event_member_status
    DROP CONSTRAINT IF EXISTS event_member_status_status_check;

ALTER TABLE event_member_status
    ADD CONSTRAINT event_member_status_status_check
        CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID', 'SURPLUS', 'OVERDUE', 'NOT_SEEN', 'SEEN'));

ALTER TABLE event_member_status
    ADD COLUMN public_ack_token UUID UNIQUE,
    ADD COLUMN seen_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_ems_public_ack_token ON event_member_status (public_ack_token);
