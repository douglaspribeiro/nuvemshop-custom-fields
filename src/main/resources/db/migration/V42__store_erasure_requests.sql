ALTER TABLE stores ADD COLUMN erasure_requested_at DATETIME(6) NULL;
ALTER TABLE stores ADD COLUMN departure_reason VARCHAR(160) NULL;
ALTER TABLE stores ADD COLUMN departure_justification VARCHAR(2000) NULL;
CREATE INDEX idx_stores_erasure_requested ON stores (erasure_requested_at);
ALTER TABLE winback_emails ADD COLUMN erasure_contact_request_at DATETIME(6) NULL;
