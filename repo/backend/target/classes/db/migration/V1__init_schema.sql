-- ============================================================
-- Meridian Training Analytics Management System
-- V1: Complete initial schema
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Organizations (tenants for corporate mentors)
CREATE TABLE organizations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- User status lifecycle
CREATE TYPE user_status AS ENUM ('PENDING','ACTIVE','LOCKED','REJECTED');

-- Users
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    organization_id UUID REFERENCES organizations(id),
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    employee_id_enc TEXT,
    contact_enc     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_users_status      ON users(status);
CREATE INDEX idx_users_org         ON users(organization_id);

-- Roles
CREATE TABLE roles (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE
);

-- User ↔ Role junction
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

-- Refresh token allow-list (enables immediate revocation)
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rt_user ON refresh_tokens(user_id);

-- Courses
CREATE TABLE courses (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title      VARCHAR(255) NOT NULL,
    version    VARCHAR(50) NOT NULL DEFAULT '1.0',
    location   VARCHAR(255),
    instructor VARCHAR(255),
    capacity   INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

-- Assessment items
CREATE TABLE assessment_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       UUID NOT NULL REFERENCES courses(id),
    question        TEXT NOT NULL,
    correct_answer  TEXT NOT NULL,
    knowledge_point VARCHAR(255),
    difficulty      NUMERIC(3,2) NOT NULL DEFAULT 0.50,
    discrimination  NUMERIC(3,2) NOT NULL DEFAULT 0.50,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ai_course         ON assessment_items(course_id);
CREATE INDEX idx_ai_knowledge_pt   ON assessment_items(knowledge_point);

-- Enrollments
CREATE TABLE enrollments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    course_id   UUID NOT NULL REFERENCES courses(id),
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    UNIQUE(user_id, course_id)
);
CREATE INDEX idx_enroll_user   ON enrollments(user_id);
CREATE INDEX idx_enroll_course ON enrollments(course_id);

-- Training sessions
CREATE TYPE session_status AS ENUM ('IN_PROGRESS','COMPLETED','INTERRUPTED');
CREATE TYPE sync_status    AS ENUM ('SYNCED','PENDING','CONFLICT','VERSION_MISMATCH');

CREATE TABLE training_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    course_id       UUID NOT NULL REFERENCES courses(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    rest_timer_secs INT NOT NULL DEFAULT 60,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    idempotency_key VARCHAR(255) UNIQUE,
    course_version  VARCHAR(50),
    sync_status     VARCHAR(20) NOT NULL DEFAULT 'SYNCED',
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_ts_user   ON training_sessions(user_id);
CREATE INDEX idx_ts_course ON training_sessions(course_id);

-- Session activities (check-offs)
CREATE TABLE session_activities (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id   UUID NOT NULL REFERENCES training_sessions(id) ON DELETE CASCADE,
    activity_ref VARCHAR(255) NOT NULL,
    completed    BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_sa_session ON session_activities(session_id);

-- Assessment attempts
CREATE TABLE attempts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id),
    assessment_item_id UUID NOT NULL REFERENCES assessment_items(id),
    answer             TEXT,
    is_correct         BOOLEAN NOT NULL DEFAULT FALSE,
    attempted_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_att_user ON attempts(user_id);
CREATE INDEX idx_att_item ON attempts(assessment_item_id);
CREATE INDEX idx_att_time ON attempts(attempted_at);

-- Training materials (inventory)
CREATE TABLE training_materials (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       UUID REFERENCES courses(id),
    name            VARCHAR(255) NOT NULL,
    quantity_on_hand INT NOT NULL DEFAULT 0,
    reorder_level   INT NOT NULL DEFAULT 5,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Certifications
CREATE TABLE certifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    course_id   UUID REFERENCES courses(id),
    cert_name   VARCHAR(255) NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_cert_user    ON certifications(user_id);
CREATE INDEX idx_cert_expires ON certifications(expires_at);

-- Notification templates
CREATE TABLE notification_templates (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL UNIQUE,
    subject    VARCHAR(255) NOT NULL,
    body       TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Notifications
CREATE TYPE notif_type AS ENUM ('APPROVAL_REQUEST','ACCOUNT_STATUS','EXPORT_COMPLETE','ANOMALY_ALERT','SYSTEM');

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id),
    type       VARCHAR(30) NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    body       TEXT NOT NULL,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notif_user   ON notifications(user_id);
CREATE INDEX idx_notif_unread ON notifications(user_id) WHERE read_at IS NULL;

-- Report schedules (Quartz-backed)
CREATE TYPE report_type   AS ENUM ('ENROLLMENTS','SEAT_UTILIZATION','REFUNDS','INVENTORY','CERTIFICATIONS_EXPIRING');
CREATE TYPE report_format AS ENUM ('CSV','PDF');

CREATE TABLE report_schedules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    organization_id UUID REFERENCES organizations(id),
    report_type     VARCHAR(30) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    output_format   VARCHAR(10) NOT NULL DEFAULT 'CSV',
    output_path     VARCHAR(500) NOT NULL,
    last_run_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Data classification + masking permissions
CREATE TYPE classification AS ENUM ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED');

CREATE TABLE data_permissions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    field_name     VARCHAR(100) NOT NULL,
    classification VARCHAR(20) NOT NULL,
    granted_by     UUID REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, field_name)
);

-- Audit events
CREATE TYPE audit_event_type AS ENUM (
    'LOGIN_SUCCESS','LOGIN_FAILURE','LOGOUT',
    'EXPORT','PERMISSION_CHANGE','DATA_DELETE','DATA_ACCESS',
    'BACKUP_TRIGGERED','RESTORE','APPROVAL_GRANTED','APPROVAL_REJECTED'
);

CREATE TABLE audit_events (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID REFERENCES users(id),
    event_type         VARCHAR(40) NOT NULL,
    entity_type        VARCHAR(100),
    entity_id          VARCHAR(255),
    details            TEXT,
    ip_address         VARCHAR(45),
    device_fingerprint VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_user ON audit_events(user_id);
CREATE INDEX idx_audit_type ON audit_events(event_type);
CREATE INDEX idx_audit_time ON audit_events(created_at);

-- Known device fingerprints per user
CREATE TABLE device_fingerprints (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    fingerprint_hash VARCHAR(255) NOT NULL,
    last_seen        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, fingerprint_hash)
);

-- Anomaly records
CREATE TABLE anomalies (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID REFERENCES users(id),
    type               VARCHAR(100) NOT NULL,
    details            TEXT,
    ip_address         VARCHAR(45),
    device_fingerprint VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_anomaly_user ON anomalies(user_id);

-- Approval workflow
CREATE TYPE approval_status AS ENUM ('PENDING','APPROVED','REJECTED');

CREATE TABLE approvals (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES users(id),
    approver_id  UUID REFERENCES users(id),
    type         VARCHAR(100) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    entity_type  VARCHAR(100),
    entity_id    VARCHAR(255),
    notes        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at  TIMESTAMPTZ
);
CREATE INDEX idx_approval_requester ON approvals(requester_id);
CREATE INDEX idx_approval_status    ON approvals(status);

-- Backups
CREATE TABLE backups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(50) NOT NULL,
    path            VARCHAR(500) NOT NULL,
    size_bytes      BIGINT,
    retention_until TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Recycle bin (soft-delete retention)
CREATE TABLE recycle_bin (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type   VARCHAR(100) NOT NULL,
    entity_id     UUID NOT NULL,
    original_data TEXT NOT NULL,
    deleted_by    UUID REFERENCES users(id),
    deleted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '14 days'
);
CREATE INDEX idx_rb_expires ON recycle_bin(expires_at);

-- Recovery drill log
CREATE TABLE recovery_drills (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    drill_date       DATE NOT NULL,
    performed_by     UUID NOT NULL REFERENCES users(id),
    steps_completed  INT NOT NULL DEFAULT 0,
    total_steps      INT NOT NULL DEFAULT 8,
    outcome          VARCHAR(50) NOT NULL DEFAULT 'PASS',
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- System security policies (key/value store)
CREATE TABLE security_policies (
    key        VARCHAR(100) PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO security_policies(key, value) VALUES
    ('allowed_cidr_ranges',        '0.0.0.0/0'),
    ('ip_enforcement_mode',        'WARN'),
    ('export_rate_limit_per_10min','20'),
    ('backup_retention_days',      '30'),
    ('backup_path',                '/var/meridian/backups'),
    ('export_approval_threshold_mb','100');
