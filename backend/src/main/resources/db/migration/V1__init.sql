CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    phone       TEXT NOT NULL UNIQUE,
    pin_hash    TEXT NOT NULL,
    role        TEXT NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'CASHIER')),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id   BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE products (
    id                BIGSERIAL PRIMARY KEY,
    name              TEXT NOT NULL,
    sku               TEXT NOT NULL UNIQUE,
    barcode           TEXT UNIQUE,
    category_id       BIGINT NOT NULL REFERENCES categories (id),
    unit              TEXT NOT NULL DEFAULT 'each',
    cost              NUMERIC(14, 2) NOT NULL CHECK (cost >= 0),
    price             NUMERIC(14, 2) NOT NULL CHECK (price >= 0),
    tax_rate          NUMERIC(6, 4) NOT NULL DEFAULT 0.1600 CHECK (tax_rate >= 0 AND tax_rate <= 1),
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    reorder_level     INTEGER NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    emoji             TEXT NOT NULL DEFAULT '📦',
    color             TEXT NOT NULL DEFAULT '#EDEBE3',
    parent_product_id BIGINT REFERENCES products (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX products_name_idx ON products (lower(name));
CREATE INDEX products_category_idx ON products (category_id);

CREATE TABLE inventory_balances (
    product_id BIGINT PRIMARY KEY REFERENCES products (id),
    quantity   INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inventory_movements (
    id             BIGSERIAL PRIMARY KEY,
    product_id     BIGINT NOT NULL REFERENCES products (id),
    movement_type  TEXT NOT NULL CHECK (movement_type IN (
        'PURCHASE', 'SALE', 'SALE_RETURN', 'STOCK_ADJUSTMENT', 'DAMAGE', 'LOSS', 'TRANSFER', 'OPENING_BALANCE'
    )),
    quantity       INTEGER NOT NULL CHECK (quantity <> 0),
    unit_cost      NUMERIC(14, 2),
    reference_type TEXT,
    reference_id   TEXT,
    note           TEXT,
    created_by     BIGINT REFERENCES users (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX inventory_movements_product_idx ON inventory_movements (product_id, created_at DESC);

CREATE TABLE app_settings (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE OR REPLACE FUNCTION inventory_prevent_negative()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    prevent text;
BEGIN
    SELECT value INTO prevent FROM app_settings WHERE key = 'prevent_negative_stock';
    IF COALESCE(prevent, 'true') = 'true' AND NEW.quantity < 0 THEN
        RAISE EXCEPTION 'negative_stock_not_allowed' USING ERRCODE = '23514';
    END IF;
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER inventory_balances_guard
    BEFORE INSERT OR UPDATE ON inventory_balances
    FOR EACH ROW EXECUTE FUNCTION inventory_prevent_negative();

CREATE TABLE suppliers (
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT NOT NULL,
    contact    TEXT NOT NULL DEFAULT '',
    products   TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE customers (
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT NOT NULL,
    phone      TEXT NOT NULL DEFAULT '',
    balance    NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE documents (
    id            BIGSERIAL PRIMARY KEY,
    original_name TEXT NOT NULL,
    content_type  TEXT,
    storage_path  TEXT NOT NULL,
    uploaded_by   BIGINT REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stock_receipts (
    id              BIGSERIAL PRIMARY KEY,
    supplier_id     BIGINT REFERENCES suppliers (id),
    supplier_name   TEXT,
    invoice_number  TEXT,
    received_date   DATE NOT NULL DEFAULT CURRENT_DATE,
    document_id     BIGINT REFERENCES documents (id),
    status          TEXT NOT NULL CHECK (status IN ('DRAFT', 'REVIEW', 'APPROVED', 'REJECTED')),
    approved_by     BIGINT REFERENCES users (id),
    approved_at     TIMESTAMPTZ,
    notes           TEXT,
    created_by      BIGINT REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX stock_receipts_status_idx ON stock_receipts (status, created_at DESC);

CREATE TABLE stock_receipt_items (
    id         BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL REFERENCES stock_receipts (id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products (id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    unit_cost  NUMERIC(14, 2) NOT NULL CHECK (unit_cost >= 0),
    tax_rate   NUMERIC(6, 4) NOT NULL DEFAULT 0
);

CREATE TABLE receipt_extractions (
    id               BIGSERIAL PRIMARY KEY,
    document_id      BIGINT REFERENCES documents (id),
    supplier_name    TEXT,
    invoice_number   TEXT,
    status           TEXT NOT NULL CHECK (status IN ('DRAFT', 'REVIEW', 'APPROVED', 'REJECTED')),
    stock_receipt_id BIGINT REFERENCES stock_receipts (id),
    reviewed_by      BIGINT REFERENCES users (id),
    reviewed_at      TIMESTAMPTZ,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX receipt_extractions_status_idx ON receipt_extractions (status, created_at DESC);

CREATE TABLE extraction_lines (
    id                 BIGSERIAL PRIMARY KEY,
    extraction_id      BIGINT NOT NULL REFERENCES receipt_extractions (id) ON DELETE CASCADE,
    raw_name           TEXT NOT NULL,
    quantity           INTEGER NOT NULL CHECK (quantity > 0),
    unit_cost          NUMERIC(14, 2) NOT NULL CHECK (unit_cost >= 0),
    barcode            TEXT,
    matched_product_id BIGINT REFERENCES products (id),
    suggested_product_id BIGINT REFERENCES products (id),
    match_method       TEXT,
    confidence         NUMERIC(5, 4),
    removed            BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE supplier_product_maps (
    id            BIGSERIAL PRIMARY KEY,
    supplier_name TEXT NOT NULL,
    raw_name      TEXT NOT NULL,
    product_id    BIGINT NOT NULL REFERENCES products (id),
    UNIQUE (supplier_name, raw_name)
);

CREATE TABLE sales (
    id               BIGSERIAL PRIMARY KEY,
    status           TEXT NOT NULL CHECK (status IN ('AWAITING_PAYMENT', 'COMPLETED', 'VOID')),
    method           TEXT NOT NULL,
    subtotal         NUMERIC(14, 2) NOT NULL,
    discount         NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (discount >= 0),
    tax              NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total            NUMERIC(14, 2) NOT NULL CHECK (total >= 0),
    customer_id      BIGINT REFERENCES customers (id),
    idempotency_key  TEXT UNIQUE,
    stock_applied    BOOLEAN NOT NULL DEFAULT FALSE,
    refunded         BOOLEAN NOT NULL DEFAULT FALSE,
    created_by       BIGINT REFERENCES users (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX sales_created_idx ON sales (created_at DESC);

CREATE TABLE sale_items (
    id         BIGSERIAL PRIMARY KEY,
    sale_id    BIGINT NOT NULL REFERENCES sales (id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products (id),
    line_no    INTEGER NOT NULL,
    name       TEXT NOT NULL,
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    unit_cost  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    refunded   BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (sale_id, line_no)
);

CREATE TABLE payments (
    id                BIGSERIAL PRIMARY KEY,
    sale_id           BIGINT NOT NULL REFERENCES sales (id),
    method            TEXT NOT NULL CHECK (method IN ('CASH', 'MPESA', 'CARD', 'CREDIT')),
    amount            NUMERIC(14, 2) NOT NULL CHECK (amount >= 0),
    tendered          NUMERIC(14, 2),
    status            TEXT NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    provider          TEXT NOT NULL,
    external_ref      TEXT,
    provider_receipt  TEXT,
    failure_reason    TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX payments_external_ref_uidx ON payments (external_ref) WHERE external_ref IS NOT NULL;
CREATE INDEX payments_sale_idx ON payments (sale_id);

CREATE TABLE etims_submissions (
    id           BIGSERIAL PRIMARY KEY,
    sale_id      BIGINT NOT NULL UNIQUE REFERENCES sales (id),
    status       TEXT NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'FAILED')),
    message      TEXT,
    external_ref TEXT,
    submitted_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE branches (
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT NOT NULL,
    sales      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    staff      INTEGER NOT NULL DEFAULT 1,
    low_stock  INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE warehouses (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    branch      TEXT NOT NULL,
    stock_value NUMERIC(14, 2) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE expenses (
    id          TEXT PRIMARY KEY,
    amount      NUMERIC(14, 2) NOT NULL CHECK (amount >= 0),
    category    TEXT NOT NULL,
    method      TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE team_members (
    id     TEXT PRIMARY KEY,
    name   TEXT NOT NULL,
    phone  TEXT NOT NULL DEFAULT '',
    role   TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('Active', 'Off shift'))
);

CREATE TABLE purchase_orders (
    id         TEXT PRIMARY KEY,
    supplier   TEXT NOT NULL,
    items      INTEGER NOT NULL,
    total      NUMERIC(14, 2) NOT NULL,
    status     TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_events (
    id         TEXT PRIMARY KEY,
    actor      TEXT NOT NULL,
    action     TEXT NOT NULL,
    detail     TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_events_created_idx ON audit_events (created_at DESC);
