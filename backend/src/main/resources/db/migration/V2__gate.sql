-- V2: gate station module

CREATE TABLE gates (
    id          UUID PRIMARY KEY,
    factory_id  UUID        NOT NULL REFERENCES factories (id),
    name        VARCHAR(80) NOT NULL,
    direction   VARCHAR(16) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (factory_id, name)
);

CREATE TABLE kiosks (
    id                UUID PRIMARY KEY,
    gate_id           UUID        NOT NULL REFERENCES gates (id),
    factory_id        UUID        NOT NULL REFERENCES factories (id),
    name              VARCHAR(80) NOT NULL,
    secret_enc        TEXT        NOT NULL,
    status            VARCHAR(16) NOT NULL,
    config_version    INTEGER     NOT NULL DEFAULT 1,
    last_heartbeat_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (gate_id, name)
);

CREATE INDEX idx_kiosks_gate ON kiosks (gate_id);
