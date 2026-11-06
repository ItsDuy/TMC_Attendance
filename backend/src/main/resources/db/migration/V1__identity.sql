-- V1: identity module (factories, employees, QR credentials)

CREATE TABLE factories (
    id          UUID PRIMARY KEY,
    code        VARCHAR(16)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE employees (
    id             UUID PRIMARY KEY,
    employee_code  VARCHAR(32)  NOT NULL UNIQUE,
    full_name      VARCHAR(160) NOT NULL,
    factory_id     UUID         NOT NULL REFERENCES factories (id),
    department     VARCHAR(120) NOT NULL,
    email          VARCHAR(190) NOT NULL UNIQUE,
    phone_enc      TEXT,
    status         VARCHAR(16)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_employees_factory ON employees (factory_id);

CREATE TABLE qr_credentials (
    employee_id  UUID PRIMARY KEY REFERENCES employees (id) ON DELETE CASCADE,
    version      INTEGER     NOT NULL DEFAULT 1,
    status       VARCHAR(16) NOT NULL,
    rotated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at   TIMESTAMPTZ
);
