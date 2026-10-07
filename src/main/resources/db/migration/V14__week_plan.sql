-- Planification hebdomadaire : e-mail avec lien pour saisir les tâches de la semaine
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS week_plan_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS week_plan_day SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS week_plan_send_time TIME NOT NULL DEFAULT '08:00',
    ADD COLUMN IF NOT EXISTS week_plan_token UUID,
    ADD COLUMN IF NOT EXISTS week_plan_last_sent_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_week_plan_token
    ON users (week_plan_token)
    WHERE week_plan_token IS NOT NULL;
