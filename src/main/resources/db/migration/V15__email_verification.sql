ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_verification_token UUID,
    ADD COLUMN IF NOT EXISTS email_verification_sent_at TIMESTAMPTZ;

-- Comptes déjà existants : considérés vérifiés pour ne pas les bloquer
UPDATE users SET email_verified = TRUE WHERE email_verified = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_verification_token
    ON users (email_verification_token)
    WHERE email_verification_token IS NOT NULL;
