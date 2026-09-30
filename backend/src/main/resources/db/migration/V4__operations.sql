CREATE TABLE businesses (
    id            BIGSERIAL PRIMARY KEY,
    name          TEXT NOT NULL,
    business_type TEXT NOT NULL DEFAULT '',
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO businesses (name, business_type, active)
SELECT COALESCE(value::json->>'name', 'Duka'), COALESCE(value::json->>'type', ''), TRUE
FROM app_settings
WHERE key = 'business'
  AND NOT EXISTS (SELECT 1 FROM businesses);

INSERT INTO businesses (name, business_type, active)
SELECT 'Duka', '', TRUE
WHERE NOT EXISTS (SELECT 1 FROM businesses);

ALTER TABLE users ADD COLUMN business_id BIGINT REFERENCES businesses (id);
UPDATE users SET business_id = (SELECT MIN(id) FROM businesses) WHERE business_id IS NULL;
ALTER TABLE users ALTER COLUMN business_id SET NOT NULL;
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('OWNER', 'MANAGER', 'CASHIER', 'STOREKEEPER'));

CREATE TABLE user_permission_overrides (
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission TEXT NOT NULL,
    granted    BOOLEAN NOT NULL,
    PRIMARY KEY (user_id, permission)
);

ALTER TABLE categories ADD COLUMN business_id BIGINT REFERENCES businesses (id);
ALTER TABLE categories ADD COLUMN parent_id BIGINT REFERENCES categories (id);
UPDATE categories SET business_id = (SELECT MIN(id) FROM businesses) WHERE business_id IS NULL;
ALTER TABLE categories ALTER COLUMN business_id SET NOT NULL;
ALTER TABLE categories DROP CONSTRAINT categories_name_key;
CREATE UNIQUE INDEX categories_business_name_parent_uidx
    ON categories (business_id, lower(name), COALESCE(parent_id, 0));

ALTER TABLE products ADD COLUMN business_id BIGINT REFERENCES businesses (id);
ALTER TABLE products ADD COLUMN brand TEXT NOT NULL DEFAULT '';
ALTER TABLE products ADD COLUMN tax_category TEXT NOT NULL DEFAULT 'STANDARD';
ALTER TABLE products ADD COLUMN supplier_id BIGINT REFERENCES suppliers (id);
UPDATE products SET business_id = (SELECT MIN(id) FROM businesses) WHERE business_id IS NULL;
ALTER TABLE products ALTER COLUMN business_id SET NOT NULL;

ALTER TABLE suppliers ADD COLUMN business_id BIGINT REFERENCES businesses (id);
ALTER TABLE suppliers ADD COLUMN phone TEXT NOT NULL DEFAULT '';
ALTER TABLE suppliers ADD COLUMN email TEXT NOT NULL DEFAULT '';
ALTER TABLE suppliers ADD COLUMN address TEXT NOT NULL DEFAULT '';
ALTER TABLE suppliers ADD COLUMN category TEXT NOT NULL DEFAULT '';
ALTER TABLE suppliers ADD COLUMN notes TEXT NOT NULL DEFAULT '';
ALTER TABLE suppliers ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE suppliers ADD COLUMN balance NUMERIC(14, 2) NOT NULL DEFAULT 0;
UPDATE suppliers SET business_id = (SELECT MIN(id) FROM businesses) WHERE business_id IS NULL;
UPDATE suppliers SET phone = contact WHERE phone = '' AND contact <> '';
ALTER TABLE suppliers ALTER COLUMN business_id SET NOT NULL;

CREATE TABLE supplier_offers (
    id           BIGSERIAL PRIMARY KEY,
    supplier_id  BIGINT NOT NULL REFERENCES suppliers (id),
    product_id   BIGINT NOT NULL REFERENCES products (id),
    supplier_sku TEXT NOT NULL DEFAULT '',
    alias        TEXT NOT NULL DEFAULT '',
    unit_cost    NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (supplier_id, product_id)
);

ALTER TABLE customers ADD COLUMN business_id BIGINT REFERENCES businesses (id);
UPDATE customers SET business_id = (SELECT MIN(id) FROM businesses) WHERE business_id IS NULL;
ALTER TABLE customers ALTER COLUMN business_id SET NOT NULL;

ALTER TABLE purchase_orders ADD COLUMN business_id BIGINT REFERENCES businesses (id);
ALTER TABLE purchase_orders ADD COLUMN supplier_id BIGINT REFERENCES suppliers (id);
ALTER TABLE purchase_orders ADD COLUMN stock_applied BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE purchase_orders SET business_id = (SELECT MIN(id) FROM businesses) WHERE business_id IS NULL;
ALTER TABLE purchase_orders ALTER COLUMN business_id SET NOT NULL;

CREATE TABLE purchase_order_lines (
    id         BIGSERIAL PRIMARY KEY,
    order_id   TEXT NOT NULL REFERENCES purchase_orders (id),
    product_id BIGINT NOT NULL REFERENCES products (id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    unit_cost  NUMERIC(14, 2) NOT NULL CHECK (unit_cost >= 0)
);

ALTER TABLE expenses ADD COLUMN business_id BIGINT REFERENCES businesses (id);
UPDATE expenses SET business_id = (SELECT MIN(id) FROM businesses) WHERE business_id IS NULL;
ALTER TABLE expenses ALTER COLUMN business_id SET NOT NULL;

ALTER TABLE branches ADD COLUMN business_id BIGINT REFERENCES businesses (id);
UPDATE branches SET business_id = (SELECT MIN(id) FROM businesses) WHERE business_id IS NULL;
ALTER TABLE branches ALTER COLUMN business_id SET NOT NULL;

UPDATE sales
SET business_id = (SELECT MIN(id)::text FROM businesses)
WHERE business_id IS NULL OR btrim(business_id) = '' OR business_id = 'unassigned';

ALTER TABLE audit_events ADD COLUMN business_id BIGINT REFERENCES businesses (id);
ALTER TABLE audit_events ADD COLUMN entity_type TEXT NOT NULL DEFAULT '';
ALTER TABLE audit_events ADD COLUMN entity_id TEXT NOT NULL DEFAULT '';
ALTER TABLE audit_events ADD COLUMN device_id TEXT NOT NULL DEFAULT '';
UPDATE audit_events SET business_id = (SELECT MIN(id) FROM businesses) WHERE business_id IS NULL;

CREATE TABLE plans (
    code        TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT ''
);

CREATE TABLE plan_modules (
    id         BIGSERIAL PRIMARY KEY,
    plan_code  TEXT NOT NULL REFERENCES plans (code),
    module_key TEXT NOT NULL,
    UNIQUE (plan_code, module_key)
);

CREATE TABLE subscriptions (
    id           BIGSERIAL PRIMARY KEY,
    business_id  BIGINT NOT NULL REFERENCES businesses (id),
    plan_code    TEXT NOT NULL REFERENCES plans (code),
    status       TEXT NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED')),
    provider     TEXT NOT NULL DEFAULT 'unconfigured',
    provider_ref TEXT,
    started_at   TIMESTAMPTZ,
    renews_at    TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    grace_until  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX subscriptions_provider_ref_uidx ON subscriptions (provider, provider_ref) WHERE provider_ref IS NOT NULL;

CREATE TABLE platform_admins (
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT NOT NULL,
    phone      TEXT NOT NULL UNIQUE,
    pin_hash   TEXT NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE devices (
    device_id       TEXT PRIMARY KEY,
    business_id     BIGINT REFERENCES businesses (id),
    branch_id       BIGINT REFERENCES branches (id),
    installation_id TEXT,
    name            TEXT NOT NULL DEFAULT '',
    app_version     TEXT NOT NULL DEFAULT '',
    last_seen       TIMESTAMPTZ,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE shifts (
    id            BIGSERIAL PRIMARY KEY,
    business_id   BIGINT NOT NULL REFERENCES businesses (id),
    user_id       BIGINT REFERENCES users (id),
    device_id     TEXT,
    opened_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at     TIMESTAMPTZ,
    opening_cash  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    expected_cash NUMERIC(14, 2),
    declared_cash NUMERIC(14, 2),
    variance      NUMERIC(14, 2)
);

CREATE TABLE support_cases (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses (id),
    opened_by   BIGINT REFERENCES users (id),
    topic       TEXT NOT NULL,
    summary     TEXT NOT NULL,
    status      TEXT NOT NULL CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE support_messages (
    id          BIGSERIAL PRIMARY KEY,
    case_id     BIGINT NOT NULL REFERENCES support_cases (id) ON DELETE CASCADE,
    author_kind TEXT NOT NULL CHECK (author_kind IN ('CUSTOMER', 'PLATFORM')),
    author_name TEXT NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO plans (code, name, description) VALUES
    ('core', 'Core', 'Selling, inventory, customers, and expenses.'),
    ('growth', 'Growth', 'Purchasing, customer credit, advanced reports, multi-branch, and the eTIMS queue.');

INSERT INTO plan_modules (plan_code, module_key) VALUES
    ('growth', 'purchasing'),
    ('growth', 'credit'),
    ('growth', 'advReports'),
    ('growth', 'multiBranch'),
    ('growth', 'etims');
