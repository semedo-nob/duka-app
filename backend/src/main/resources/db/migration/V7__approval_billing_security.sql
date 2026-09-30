ALTER TABLE businesses DROP CONSTRAINT IF EXISTS businesses_status_check;
ALTER TABLE businesses ADD CONSTRAINT businesses_status_check CHECK (status IN (
    'REGISTERED', 'TRIAL', 'ACTIVE', 'SUSPENDED', 'REACTIVATED', 'CANCELLED', 'DEACTIVATED',
    'SUBSCRIPTION_EXPIRED', 'PENDING_VERIFICATION', 'PENDING_APPROVAL', 'CLOSED'
));
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS status_reason TEXT NOT NULL DEFAULT '';
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMPTZ;
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS status_changed_by TEXT NOT NULL DEFAULT '';

ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash TEXT;

ALTER TABLE plans ADD COLUMN IF NOT EXISTS branch_limit INTEGER;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS user_limit INTEGER;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS device_limit INTEGER;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS product_limit INTEGER;

UPDATE plans SET branch_limit = 1, user_limit = 5, device_limit = 3
WHERE code = 'core' AND branch_limit IS NULL;
UPDATE plans SET branch_limit = 10, user_limit = 40, device_limit = 20, product_limit = NULL
WHERE code = 'growth' AND branch_limit IS NULL;

ALTER TABLE subscription_payments DROP CONSTRAINT IF EXISTS subscription_payments_status_check;
ALTER TABLE subscription_payments ADD CONSTRAINT subscription_payments_status_check
    CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED'));
ALTER TABLE subscription_payments ADD COLUMN IF NOT EXISTS failure_reason TEXT NOT NULL DEFAULT '';

ALTER TABLE support_cases DROP CONSTRAINT IF EXISTS support_cases_category_check;
ALTER TABLE support_cases ADD CONSTRAINT support_cases_category_check CHECK (category IN (
    'SUBSCRIPTION', 'BILLING', 'PAYMENT', 'TECHNICAL', 'ACCOUNT', 'DEVICE', 'PRINTER', 'SCANNER',
    'M-PESA', 'ETIMS', 'SYNC', 'OFFLINE', 'OTHER'
));
