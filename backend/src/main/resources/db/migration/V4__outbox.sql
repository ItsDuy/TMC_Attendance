-- V4: transactional outbox (ADR-006)

CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    event_type     VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(48) NOT NULL,
    aggregate_id   VARCHAR(64) NOT NULL,
    payload        JSONB       NOT NULL,
    status         VARCHAR(16) NOT NULL,
    attempts       INTEGER     NOT NULL DEFAULT 0,
    last_error     VARCHAR(400),
    next_attempt_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox_events (status, created_at) WHERE status = 'PENDING';
