ALTER TABLE order_drafts ADD COLUMN original_transcript TEXT;
ALTER TABLE order_drafts ADD COLUMN language_hint VARCHAR(16);
UPDATE order_drafts SET original_transcript = transcript WHERE original_transcript IS NULL;
