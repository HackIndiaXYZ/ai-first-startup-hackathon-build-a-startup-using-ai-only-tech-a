ALTER TABLE owners
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN google_subject VARCHAR(255),
    ADD COLUMN street VARCHAR(200),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN state VARCHAR(100),
    ADD COLUMN postal_code VARCHAR(20);

CREATE UNIQUE INDEX uq_owners_google_subject ON owners (google_subject)
    WHERE google_subject IS NOT NULL;

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES owners (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_email_verification_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_verification_tokens_owner_id ON email_verification_tokens (owner_id);
