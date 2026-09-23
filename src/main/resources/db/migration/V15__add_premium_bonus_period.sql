ALTER TABLE stores
    ADD COLUMN premium_bonus_started_at TIMESTAMP(6) NULL,
    ADD COLUMN premium_bonus_expires_at TIMESTAMP(6) NULL;
