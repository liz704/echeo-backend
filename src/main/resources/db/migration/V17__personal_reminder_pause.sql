-- V17: pause for personal reminders.
-- A paused reminder stays in the active list but sends no e-mail and is not rolled forward.
ALTER TABLE personal_reminders
    ADD COLUMN IF NOT EXISTS is_paused BOOLEAN NOT NULL DEFAULT FALSE;
