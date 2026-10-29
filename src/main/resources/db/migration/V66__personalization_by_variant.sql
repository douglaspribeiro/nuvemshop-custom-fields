ALTER TABLE personalization_rules
    ADD COLUMN variant_restricted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN personalization_variant_ids TEXT NULL;
