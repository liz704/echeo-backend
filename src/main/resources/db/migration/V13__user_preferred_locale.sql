-- Langue préférée pour les e-mails (fr ou en). Défaut français.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS preferred_locale VARCHAR(5) NOT NULL DEFAULT 'fr';
