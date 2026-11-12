-- Existing fields remain single-file; only Ultra can configure up to five.
ALTER TABLE personalization_fields ADD COLUMN max_files INT NOT NULL DEFAULT 1;
