-- ============================================================================
-- ÉCHÉO - Schéma PostgreSQL initial (base : echeo1)
-- Migration Flyway : V1__init_schema.sql
-- ============================================================================

-- Extension nécessaire pour la génération d'UUID (gen_random_uuid())
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- 1. TABLE users
-- ============================================================================
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone           VARCHAR(30),
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER'
                     CHECK (role IN ('USER', 'ADMIN')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);

-- ============================================================================
-- 2. TABLE personal_reminders
-- ============================================================================
CREATE TABLE personal_reminders (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL
                       REFERENCES users (id) ON DELETE CASCADE,
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    due_date          DATE NOT NULL,
    due_time          TIME,
    repetition_type   VARCHAR(20) NOT NULL DEFAULT 'NONE'
                       CHECK (repetition_type IN ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY')),
    is_completed      BOOLEAN NOT NULL DEFAULT FALSE,
    next_occurrence   DATE
);

-- Index critiques pour le moteur de relances (recherche par échéance / statut)
CREATE INDEX idx_reminders_user_id ON personal_reminders (user_id);
CREATE INDEX idx_reminders_due_date ON personal_reminders (due_date);
CREATE INDEX idx_reminders_next_occurrence ON personal_reminders (next_occurrence);
CREATE INDEX idx_reminders_pending_due
    ON personal_reminders (due_date, is_completed)
    WHERE is_completed = FALSE;

-- ============================================================================
-- 3. TABLE groups
-- ============================================================================
CREATE TABLE groups (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    owner_id     BIGINT NOT NULL
                 REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_groups_owner_id ON groups (owner_id);

-- ============================================================================
-- 4. TABLE group_members
-- ============================================================================
CREATE TABLE group_members (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT NOT NULL
               REFERENCES groups (id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL
               REFERENCES users (id) ON DELETE CASCADE,
    joined_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_group_member UNIQUE (group_id, user_id)
);

CREATE INDEX idx_group_members_group_id ON group_members (group_id);
CREATE INDEX idx_group_members_user_id ON group_members (user_id);

-- ============================================================================
-- 5. TABLE group_events
-- ============================================================================
CREATE TABLE group_events (
    id             BIGSERIAL PRIMARY KEY,
    group_id       BIGINT NOT NULL
                   REFERENCES groups (id) ON DELETE CASCADE,
    title          VARCHAR(255) NOT NULL,
    description    TEXT,
    target_amount  NUMERIC(14, 2) NOT NULL CHECK (target_amount >= 0),
    event_date     DATE NOT NULL
);

CREATE INDEX idx_group_events_group_id ON group_events (group_id);
CREATE INDEX idx_group_events_event_date ON group_events (event_date);

-- ============================================================================
-- 6. TABLE event_member_status
-- ============================================================================
CREATE TABLE event_member_status (
    id               BIGSERIAL PRIMARY KEY,
    event_id         BIGINT NOT NULL
                     REFERENCES group_events (id) ON DELETE CASCADE,
    member_id        BIGINT NOT NULL
                     REFERENCES users (id) ON DELETE CASCADE,
    required_amount  NUMERIC(14, 2) NOT NULL CHECK (required_amount >= 0),
    paid_amount      NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID', 'SURPLUS', 'OVERDUE')),
    CONSTRAINT uq_event_member UNIQUE (event_id, member_id)
);

-- Index critiques pour le moteur de relances (statuts en retard / partiels)
CREATE INDEX idx_ems_event_id ON event_member_status (event_id);
CREATE INDEX idx_ems_member_id ON event_member_status (member_id);
CREATE INDEX idx_ems_status ON event_member_status (status);
CREATE INDEX idx_ems_status_event
    ON event_member_status (status, event_id)
    WHERE status IN ('PENDING', 'PARTIALLY_PAID', 'OVERDUE');

-- ============================================================================
-- 7. TABLE payment_history
-- ============================================================================
CREATE TABLE payment_history (
    id                       BIGSERIAL PRIMARY KEY,
    event_member_status_id  BIGINT NOT NULL
                             REFERENCES event_member_status (id) ON DELETE CASCADE,
    amount_paid              NUMERIC(14, 2) NOT NULL CHECK (amount_paid > 0),
    payment_method           VARCHAR(20) NOT NULL
                              CHECK (payment_method IN ('CASH', 'MOBILE_MONEY', 'CARD')),
    transaction_ref          VARCHAR(255),
    paid_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_history_ems_id ON payment_history (event_member_status_id);
CREATE INDEX idx_payment_history_paid_at ON payment_history (paid_at);

-- ============================================================================
-- 8. TABLE payment_tokens (liens de paiement à usage unique, ex. paiement en ligne)
-- ============================================================================
CREATE TABLE payment_tokens (
    id                       BIGSERIAL PRIMARY KEY,
    token_uuid               UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    event_member_status_id  BIGINT NOT NULL
                             REFERENCES event_member_status (id) ON DELETE CASCADE,
    expires_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    is_used                  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_payment_tokens_ems_id ON payment_tokens (event_member_status_id);
CREATE INDEX idx_payment_tokens_uuid ON payment_tokens (token_uuid);
-- Index pour le job de nettoyage/validation des tokens expirés non utilisés
CREATE INDEX idx_payment_tokens_expiry
    ON payment_tokens (expires_at, is_used)
    WHERE is_used = FALSE;

-- ============================================================================
-- 9. TABLE notification_logs
-- ============================================================================
CREATE TABLE notification_logs (
    id                 BIGSERIAL PRIMARY KEY,
    recipient_contact  VARCHAR(255) NOT NULL,
    type               VARCHAR(20) NOT NULL
                        CHECK (type IN ('EMAIL', 'SMS', 'WHATSAPP')),
    message_content    TEXT NOT NULL,
    sent_at            TIMESTAMP WITH TIME ZONE,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_notification_logs_status ON notification_logs (status);
CREATE INDEX idx_notification_logs_sent_at ON notification_logs (sent_at);
CREATE INDEX idx_notification_logs_recipient ON notification_logs (recipient_contact);

-- ============================================================================
-- FIN DU SCRIPT
-- ============================================================================
