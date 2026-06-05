ALTER TABLE stores
    ADD COLUMN courtesy_premium BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN courtesy_premium_reason VARCHAR(255);
