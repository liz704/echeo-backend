-- ============================================================================
-- ÉCHÉO - Migration V2 : gestion du "mot de passe oublié"
-- Migration Flyway : V2__password_reset_tokens.sql
-- ============================================================================

CREATE TABLE password_reset_tokens (
    id           BIGSERIAL PRIMARY KEY,
    token_uuid   UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id      BIGINT NOT NULL
                 REFERENCES users (id) ON DELETE CASCADE,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    is_used      BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_uuid ON password_reset_tokens (token_uuid);
CREATE INDEX idx_password_reset_tokens_expiry
    ON password_reset_tokens (expires_at, is_used)
    WHERE is_used = FALSE;
