ALTER TABLE payment_subscriptions ADD COLUMN paid_started_at TIMESTAMP(6) NULL;

-- Legacy dates are conservative: never use checkout creation as proof of paid access.
-- Renewal periods may postpone the first prompt, but cannot make it appear too early.
UPDATE payment_subscriptions p
SET paid_started_at = GREATEST(p.created_at,
    COALESCE(p.current_period_start, p.created_at),
    (SELECT MAX(e.created_at) FROM plan_events e WHERE e.store_id=p.store_id
     AND e.to_plan IN ('PREMIUM','PREMIUM_PLUS','PREMIUM_ULTRA')
     AND e.source IN ('EFI_CHECKOUT','PAYMENT_WEBHOOK','PAYMENT_RECONCILE','PAYMENT_UPGRADE','STRIPE_CHECKOUT','CREEM_CHECKOUT','PENDING_TIMEOUT_RECONCILE')))
WHERE p.status='ACTIVE' AND p.access_active=true AND p.provider_environment='PRODUCTION'
    AND p.current_period_start IS NOT NULL;

CREATE TABLE merchant_ratings (
    id VARCHAR(36) PRIMARY KEY,
    store_id BIGINT NOT NULL,
    displayed_at TIMESTAMP(6) NOT NULL,
    rating SMALLINT NULL,
    comment TEXT NULL,
    rated_at TIMESTAMP(6) NULL,
    dismissed_at TIMESTAMP(6) NULL,
    public_review_clicked_at TIMESTAMP(6) NULL,
    INDEX ix_merchant_ratings_store_display (store_id,displayed_at),
    CONSTRAINT ck_merchant_ratings_score CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
