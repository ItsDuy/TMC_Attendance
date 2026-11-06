-- V5: security and audit

CREATE TABLE app_users (
    id            UUID PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    role          VARCHAR(24)  NOT NULL,
    employee_id   UUID REFERENCES employees (id),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id            BIGSERIAL PRIMARY KEY,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    actor         VARCHAR(80)  NOT NULL,
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(48)  NOT NULL,
    resource_id   VARCHAR(80),
    payload_hash  VARCHAR(64)  NOT NULL,
    prev_hash     VARCHAR(64)  NOT NULL,
    hash          VARCHAR(64)  NOT NULL
);

CREATE INDEX idx_audit_occurred ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_actor ON audit_log (actor);

-- Single-row chain head, locked FOR UPDATE on every append to serialise writers.
CREATE TABLE audit_chain_head (
    id        SMALLINT PRIMARY KEY,
    last_hash VARCHAR(64) NOT NULL
);
INSERT INTO audit_chain_head (id, last_hash)
VALUES (1, '0000000000000000000000000000000000000000000000000000000000000000');
