ALTER TABLE businesses ADD COLUMN legal_name TEXT NOT NULL DEFAULT '';
ALTER TABLE businesses ADD COLUMN phone TEXT NOT NULL DEFAULT '';
ALTER TABLE businesses ADD COLUMN email TEXT NOT NULL DEFAULT '';
ALTER TABLE businesses ADD COLUMN address TEXT NOT NULL DEFAULT '';
ALTER TABLE businesses ADD COLUMN category TEXT NOT NULL DEFAULT '';
ALTER TABLE businesses ADD COLUMN registration_number TEXT NOT NULL DEFAULT '';
ALTER TABLE businesses ADD COLUMN kra_pin TEXT NOT NULL DEFAULT '';
ALTER TABLE businesses ADD COLUMN currency TEXT NOT NULL DEFAULT 'KES';
ALTER TABLE businesses ADD COLUMN timezone TEXT NOT NULL DEFAULT 'Africa/Nairobi';
ALTER TABLE businesses ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE businesses ADD COLUMN receipt_footer TEXT NOT NULL DEFAULT '';
ALTER TABLE businesses ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE businesses ADD COLUMN retention_until TIMESTAMPTZ;
ALTER TABLE businesses DROP CONSTRAINT IF EXISTS businesses_status_check;
ALTER TABLE businesses ADD CONSTRAINT businesses_status_check CHECK (status IN (
    'REGISTERED', 'TRIAL', 'ACTIVE', 'SUSPENDED', 'REACTIVATED', 'CANCELLED', 'DEACTIVATED',
    'SUBSCRIPTION_EXPIRED', 'PENDING_VERIFICATION'
));

ALTER TABLE users ADD COLUMN email TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN employee_id TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN branch_id BIGINT REFERENCES branches (id);
ALTER TABLE users ADD COLUMN status TEXT;
UPDATE users SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'DISABLED' END WHERE status IS NULL;
ALTER TABLE users ALTER COLUMN status SET NOT NULL;
ALTER TABLE users ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMPTZ;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('OWNER', 'MANAGER', 'CASHIER', 'STOREKEEPER', 'INVENTORY'));
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED', 'LOCKED', 'DEACTIVATED'));
CREATE INDEX IF NOT EXISTS users_business_idx ON users (business_id);

CREATE TABLE user_sessions (
    id           TEXT PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users (id),
    business_id  BIGINT REFERENCES businesses (id),
    device_id    TEXT NOT NULL DEFAULT '',
    user_agent   TEXT NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ
);
CREATE INDEX user_sessions_user_idx ON user_sessions (user_id, created_at DESC);
CREATE INDEX user_sessions_business_idx ON user_sessions (business_id, created_at DESC);

CREATE TABLE staff_invitations (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses (id),
    user_id     BIGINT NOT NULL REFERENCES users (id),
    token_hash  TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_by  BIGINT REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE recovery_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id),
    token_hash  TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE installations (
    id           TEXT PRIMARY KEY,
    business_id  BIGINT REFERENCES businesses (id),
    app_version  TEXT NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ
);

ALTER TABLE platform_admins ADD COLUMN role TEXT NOT NULL DEFAULT 'SUPER_ADMIN';
ALTER TABLE platform_admins DROP CONSTRAINT IF EXISTS platform_admins_role_check;
ALTER TABLE platform_admins ADD CONSTRAINT platform_admins_role_check CHECK (role IN (
    'SUPER_ADMIN', 'SUPPORT_ADMIN', 'BILLING_ADMIN', 'PLATFORM_AUDITOR'
));

ALTER TABLE support_cases DROP CONSTRAINT IF EXISTS support_cases_status_check;
ALTER TABLE support_cases ADD CONSTRAINT support_cases_status_check CHECK (status IN (
    'OPEN', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED', 'ANSWERED'
));

ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS subscriptions_status_check;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_status_check CHECK (status IN (
    'TRIAL', 'PENDING', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED'
));

ALTER TABLE warehouses ADD COLUMN business_id BIGINT REFERENCES businesses (id);
UPDATE warehouses SET business_id = (SELECT MIN(id) FROM businesses) WHERE business_id IS NULL;
ALTER TABLE warehouses ALTER COLUMN business_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS warehouses_business_idx ON warehouses (business_id);
