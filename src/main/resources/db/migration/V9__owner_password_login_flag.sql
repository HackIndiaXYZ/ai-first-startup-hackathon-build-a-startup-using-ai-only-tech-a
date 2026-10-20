ALTER TABLE owners
    ADD COLUMN password_login_enabled BOOLEAN NOT NULL DEFAULT TRUE;
