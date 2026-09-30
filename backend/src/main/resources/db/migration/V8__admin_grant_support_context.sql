-- Plan lifecycle uses ACTIVE / INACTIVE. Historical ARCHIVED rows become INACTIVE.
ALTER TABLE plans DROP CONSTRAINT IF EXISTS plans_status_check;
UPDATE plans SET status = 'INACTIVE' WHERE status = 'ARCHIVED';
ALTER TABLE plans ADD CONSTRAINT plans_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE support_cases ADD COLUMN IF NOT EXISTS priority TEXT NOT NULL DEFAULT 'NORMAL';
ALTER TABLE support_cases DROP CONSTRAINT IF EXISTS support_cases_priority_check;
ALTER TABLE support_cases ADD CONSTRAINT support_cases_priority_check
    CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));

ALTER TABLE support_messages DROP CONSTRAINT IF EXISTS support_messages_author_kind_check;
ALTER TABLE support_messages ADD CONSTRAINT support_messages_author_kind_check
    CHECK (author_kind IN ('CUSTOMER', 'PLATFORM', 'CONTEXT'));
