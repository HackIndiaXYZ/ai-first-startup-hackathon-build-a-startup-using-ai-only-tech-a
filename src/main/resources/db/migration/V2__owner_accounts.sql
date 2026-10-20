CREATE TABLE owners (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_owners_email UNIQUE (email),
    CONSTRAINT ck_owners_email_normalized CHECK (email = lower(btrim(email)))
);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES owners (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_owner_id ON password_reset_tokens (owner_id);

-- Nullable on purpose: rows created before accounts existed have no owner and stay invisible to every account.
ALTER TABLE customers ADD COLUMN owner_id UUID REFERENCES owners (id);
ALTER TABLE order_drafts ADD COLUMN owner_id UUID REFERENCES owners (id);

CREATE INDEX idx_customers_owner_id ON customers (owner_id);
CREATE INDEX idx_order_drafts_owner_id ON order_drafts (owner_id);
