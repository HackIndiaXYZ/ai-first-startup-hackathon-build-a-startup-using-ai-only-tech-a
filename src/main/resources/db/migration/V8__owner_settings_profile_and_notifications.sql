ALTER TABLE owners
    ADD COLUMN shop_name VARCHAR(200),
    ADD COLUMN avatar_key VARCHAR(100),
    ADD COLUMN theme VARCHAR(16) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN in_app_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN browser_push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN payment_due_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN overdue_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN new_order_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN payment_received_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN daily_summary_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN ui_language VARCHAR(16) NOT NULL DEFAULT 'ENGLISH',
    ADD COLUMN reminder_default_language VARCHAR(16) NOT NULL DEFAULT 'HINGLISH';

ALTER TABLE customers DROP CONSTRAINT customers_owner_id_fkey;
ALTER TABLE customers ADD CONSTRAINT customers_owner_id_fkey
    FOREIGN KEY (owner_id) REFERENCES owners (id) ON DELETE CASCADE;
ALTER TABLE order_drafts DROP CONSTRAINT order_drafts_owner_id_fkey;
ALTER TABLE order_drafts ADD CONSTRAINT order_drafts_owner_id_fkey
    FOREIGN KEY (owner_id) REFERENCES owners (id) ON DELETE CASCADE;
ALTER TABLE orders DROP CONSTRAINT orders_customer_id_fkey;
ALTER TABLE orders ADD CONSTRAINT orders_customer_id_fkey
    FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE;
ALTER TABLE orders DROP CONSTRAINT orders_originating_draft_id_fkey;
ALTER TABLE orders ADD CONSTRAINT orders_originating_draft_id_fkey
    FOREIGN KEY (originating_draft_id) REFERENCES order_drafts (id) ON DELETE CASCADE;
ALTER TABLE order_items DROP CONSTRAINT order_items_order_id_fkey;
ALTER TABLE order_items ADD CONSTRAINT order_items_order_id_fkey
    FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE;
ALTER TABLE payments DROP CONSTRAINT payments_order_id_fkey;
ALTER TABLE payments ADD CONSTRAINT payments_order_id_fkey
    FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE;

CREATE TABLE owner_notifications (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES owners (id) ON DELETE CASCADE,
    kind VARCHAR(32) NOT NULL,
    reference_id UUID,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ
);

CREATE INDEX idx_owner_notifications_owner_created
    ON owner_notifications (owner_id, created_at DESC);
CREATE UNIQUE INDEX uq_owner_notifications_event
    ON owner_notifications (owner_id, kind, reference_id)
    WHERE reference_id IS NOT NULL;
