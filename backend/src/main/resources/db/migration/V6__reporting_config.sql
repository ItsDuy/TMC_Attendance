-- V6: reporting integration and runtime configuration

CREATE TABLE hr_export_batches (
    id              UUID PRIMARY KEY,
    period          VARCHAR(7)  NOT NULL,
    factory_id      UUID        NOT NULL REFERENCES factories (id),
    idempotency_key VARCHAR(80) NOT NULL UNIQUE,
    record_count    INTEGER     NOT NULL,
    checksum        VARCHAR(64) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    attempts        INTEGER     NOT NULL DEFAULT 0,
    last_error      VARCHAR(400),
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE system_config (
    config_key   VARCHAR(80) PRIMARY KEY,
    config_value VARCHAR(200) NOT NULL,
    description  VARCHAR(240),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO system_config (config_key, config_value, description) VALUES
    ('antiPassback.cooldownSeconds', '180', 'Reject a repeated scan in the same direction within this window'),
    ('antiPassback.maxOpenHours', '18', 'Reject an OUT event when no IN exists within this window'),
    ('qr.tokenTtlSeconds', '60', 'Lifetime of a minted dynamic QR token'),
    ('report.exportHour', '6', 'Hour of day (local) on the 1st when the HR export runs');
