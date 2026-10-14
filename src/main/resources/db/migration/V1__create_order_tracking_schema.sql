CREATE TABLE customers (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(16),
    preferred_reminder_language VARCHAR(32)
);

CREATE TABLE order_drafts (
    id UUID PRIMARY KEY,
    transcript TEXT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    extraction_json JSONB,
    review_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers (id),
    originating_draft_id UUID NOT NULL UNIQUE REFERENCES order_drafts (id),
    entry_type VARCHAR(32) NOT NULL,
    order_date DATE NOT NULL,
    due_date DATE,
    total NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    transcript TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_orders_total_nonnegative CHECK (total >= 0)
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id),
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(12, 3),
    unit VARCHAR(32),
    unit_price NUMERIC(12, 2),
    line_total NUMERIC(12, 2),
    CONSTRAINT ck_order_items_quantity_nonnegative CHECK (quantity IS NULL OR quantity >= 0),
    CONSTRAINT ck_order_items_unit_price_nonnegative CHECK (unit_price IS NULL OR unit_price >= 0),
    CONSTRAINT ck_order_items_line_total_nonnegative CHECK (line_total IS NULL OR line_total >= 0)
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id),
    amount NUMERIC(12, 2) NOT NULL,
    paid_at TIMESTAMPTZ NOT NULL,
    note TEXT,
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_payments_order_id ON payments (order_id);
