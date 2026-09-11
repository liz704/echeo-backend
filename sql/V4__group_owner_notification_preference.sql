-- ============================================================================
-- ÉCHÉO - Migration V4 : le propriétaire d'un groupe peut choisir de recevoir
-- (ou non) une copie de chaque relance envoyée à ses membres.
-- ============================================================================

ALTER TABLE groups
    ADD COLUMN notify_owner_on_reminders BOOLEAN NOT NULL DEFAULT TRUE;
