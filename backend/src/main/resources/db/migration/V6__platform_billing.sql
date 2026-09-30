ALTER TABLE plans ADD COLUMN price_amount NUMERIC(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE plans ADD COLUMN currency TEXT NOT NULL DEFAULT 'KES';
ALTER TABLE plans ADD COLUMN billing_interval TEXT NOT NULL DEFAULT 'MONTH';
ALTER TABLE plans ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE plans DROP CONSTRAINT IF EXISTS plans_status_check;
ALTER TABLE plans ADD CONSTRAINT plans_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'));
ALTER TABLE plans DROP CONSTRAINT IF EXISTS plans_interval_check;
ALTER TABLE plans ADD CONSTRAINT plans_interval_check CHECK (billing_interval IN ('MONTH', 'YEAR'));

UPDATE plans SET price_amount = 0, currency = 'KES', billing_interval = 'MONTH', status = 'ACTIVE' WHERE code = 'core';
UPDATE plans SET price_amount = 2499, currency = 'KES', billing_interval = 'MONTH', status = 'ACTIVE' WHERE code = 'growth';

ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS subscriptions_status_check;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_status_check CHECK (status IN (
    'TRIAL', 'PENDING', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED', 'FAILED'
));
ALTER TABLE subscriptions ADD COLUMN cancelled_at TIMESTAMPTZ;

CREATE TABLE subscription_payments (
    id              BIGSERIAL PRIMARY KEY,
    business_id     BIGINT NOT NULL REFERENCES businesses (id),
    subscription_id BIGINT REFERENCES subscriptions (id),
    plan_code       TEXT NOT NULL,
    amount          NUMERIC(14, 2) NOT NULL,
    currency        TEXT NOT NULL DEFAULT 'KES',
    status          TEXT NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    provider        TEXT NOT NULL,
    provider_ref    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_at     TIMESTAMPTZ
);
CREATE UNIQUE INDEX subscription_payments_provider_ref_uidx
    ON subscription_payments (provider, provider_ref) WHERE provider_ref IS NOT NULL;
CREATE INDEX subscription_payments_business_idx ON subscription_payments (business_id, created_at DESC);

CREATE TABLE billing_events (
    id               BIGSERIAL PRIMARY KEY,
    provider         TEXT NOT NULL,
    event_id         TEXT NOT NULL,
    event_type       TEXT NOT NULL,
    business_id      BIGINT,
    provider_ref     TEXT NOT NULL DEFAULT '',
    summary          TEXT NOT NULL DEFAULT '',
    signature_valid  BOOLEAN NOT NULL,
    processed        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, event_id)
);
CREATE INDEX billing_events_business_idx ON billing_events (business_id, created_at DESC);

ALTER TABLE devices ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 0;
ALTER TABLE devices ADD COLUMN failed_sync INTEGER NOT NULL DEFAULT 0;
ALTER TABLE devices ADD COLUMN last_sync_at TIMESTAMPTZ;
ALTER TABLE devices ADD COLUMN oldest_pending_at TIMESTAMPTZ;
ALTER TABLE devices ADD COLUMN last_error TEXT NOT NULL DEFAULT '';
ALTER TABLE devices ADD COLUMN printer_status TEXT NOT NULL DEFAULT '';
ALTER TABLE devices ADD COLUMN scanner_status TEXT NOT NULL DEFAULT '';

ALTER TABLE support_cases ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER';
ALTER TABLE support_cases DROP CONSTRAINT IF EXISTS support_cases_category_check;
ALTER TABLE support_cases ADD CONSTRAINT support_cases_category_check CHECK (category IN (
    'SUBSCRIPTION', 'BILLING', 'TECHNICAL', 'ACCOUNT', 'DEVICE', 'PRINTER', 'SCANNER', 'M-PESA', 'ETIMS', 'SYNC', 'OTHER'
));
