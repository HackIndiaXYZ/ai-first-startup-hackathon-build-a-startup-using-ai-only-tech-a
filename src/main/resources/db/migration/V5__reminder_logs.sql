CREATE TABLE reminder_logs (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES owners (id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    language VARCHAR(16) NOT NULL,
    action VARCHAR(24) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_reminder_logs_action CHECK (action IN ('DRAFT_CREATED', 'WHATSAPP_OPENED', 'SMS_OPENED'))
);

CREATE INDEX idx_reminder_logs_owner_order_time
    ON reminder_logs (owner_id, order_id, occurred_at DESC);
