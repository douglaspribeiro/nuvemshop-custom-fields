ALTER TABLE stores
    ADD COLUMN pix_provisional_plan VARCHAR(30) NULL,
    ADD COLUMN pix_provisional_until TIMESTAMP(6) NULL;
