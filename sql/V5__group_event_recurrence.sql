-- ============================================================================
-- ÉCHÉO - Migration V5 : cotisations récurrentes (groupes)
--
-- Un événement de groupe peut désormais se répéter (comme un rappel
-- personnel), être mis en pause sans être supprimé, et son échéance passée
-- déclenche automatiquement le statut OVERDUE pour les membres non soldés.
-- ============================================================================

ALTER TABLE group_events
    ADD COLUMN repetition_type VARCHAR(20) NOT NULL DEFAULT 'NONE'
        CHECK (repetition_type IN ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY')),
    ADD COLUMN next_occurrence DATE,
    ADD COLUMN is_paused BOOLEAN NOT NULL DEFAULT FALSE;

-- Index pour le job quotidien qui cherche les événements récurrents dus
-- (non déjà reconduits, non en pause, échéance passée).
CREATE INDEX idx_group_events_recurrence_due
    ON group_events (event_date, is_paused, next_occurrence)
    WHERE repetition_type <> 'NONE';
