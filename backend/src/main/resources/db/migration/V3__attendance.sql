-- V3: attendance module
-- attendance_records is range-partitioned by month (ADR-013).
-- Idempotency lives in a separate, unpartitioned table (ADR-007).

CREATE TABLE attendance_records (
    id           UUID        NOT NULL,
    employee_id  UUID        NOT NULL REFERENCES employees (id),
    gate_id      UUID        NOT NULL REFERENCES gates (id),
    kiosk_id     UUID        NOT NULL REFERENCES kiosks (id),
    factory_id   UUID        NOT NULL REFERENCES factories (id),
    direction    VARCHAR(8)  NOT NULL,
    scanned_at   TIMESTAMPTZ NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL,
    status       VARCHAR(24) NOT NULL,
    gate_outcome VARCHAR(32),
    source       VARCHAR(12) NOT NULL,
    reason       VARCHAR(160),
    PRIMARY KEY (id, scanned_at)
) PARTITION BY RANGE (scanned_at);

CREATE TABLE attendance_records_default PARTITION OF attendance_records DEFAULT;

CREATE INDEX idx_attendance_employee_time ON attendance_records (employee_id, scanned_at DESC);
CREATE INDEX idx_attendance_status ON attendance_records (status) WHERE status = 'PENDING';

CREATE TABLE processed_events (
    client_event_id UUID PRIMARY KEY,
    record_id       UUID        NOT NULL,
    result_json     TEXT        NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL
);
