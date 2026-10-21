-- Previous imports classified every item property (including other apps' gift IDs)
-- as personalization. Hide those aggregates until a complete historical import
-- has applied the same product/field matching used by the merchant order list.
-- Preserve stored rows: the importer replaces matches and removes false positives.
UPDATE store_sales_sync
SET complete = FALSE,
    personalized_value_backfilled = FALSE,
    last_attempt_at = NULL,
    last_error = NULL;
