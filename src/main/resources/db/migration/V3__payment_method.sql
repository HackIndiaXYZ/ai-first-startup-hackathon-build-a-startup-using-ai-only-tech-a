ALTER TABLE payments ADD COLUMN method VARCHAR(20);

ALTER TABLE payments ADD CONSTRAINT ck_payments_method
    CHECK (method IS NULL OR method IN ('CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER'));
