-- V4: Operational transactions audit trail
CREATE TABLE operational_transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_type VARCHAR(100) NOT NULL,
    initiated_by     UUID REFERENCES users(id) ON DELETE SET NULL,
    entity_type      VARCHAR(100),
    entity_id        VARCHAR(255),
    details          JSONB,
    status           VARCHAR(50) NOT NULL DEFAULT 'COMPLETED',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_op_tx_type        ON operational_transactions(transaction_type);
CREATE INDEX idx_op_tx_user        ON operational_transactions(initiated_by);
CREATE INDEX idx_op_tx_created     ON operational_transactions(created_at);
