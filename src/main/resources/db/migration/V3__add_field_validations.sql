ALTER TABLE personalization_fields
    ADD COLUMN validation_pattern VARCHAR(255) NULL AFTER placeholder;
