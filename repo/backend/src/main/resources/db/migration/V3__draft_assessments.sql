-- V3: Draft assessment offline sync table
CREATE TABLE draft_assessments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id       UUID REFERENCES training_sessions(id) ON DELETE CASCADE,
    item_id          UUID REFERENCES assessment_items(id),
    answer           TEXT,
    flagged          BOOLEAN NOT NULL DEFAULT FALSE,
    time_spent_secs  INT NOT NULL DEFAULT 0,
    idempotency_key  VARCHAR(255) NOT NULL UNIQUE,
    sync_status      sync_status NOT NULL DEFAULT 'PENDING',
    last_modified    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_da_session        ON draft_assessments(session_id);
CREATE INDEX idx_da_sync_status    ON draft_assessments(sync_status);
CREATE INDEX idx_da_idempotency    ON draft_assessments(idempotency_key);
