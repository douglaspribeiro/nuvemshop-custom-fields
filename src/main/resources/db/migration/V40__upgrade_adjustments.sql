CREATE TABLE upgrade_adjustments (
    id VARCHAR(36) PRIMARY KEY,
    store_id BIGINT NOT NULL,
    subscription_id VARCHAR(120) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    source_plan VARCHAR(30) NOT NULL,
    target_plan VARCHAR(30) NOT NULL,
    source_amount DECIMAL(12,2) NOT NULL,
    regular_amount DECIMAL(12,2) NOT NULL,
    target_price_id VARCHAR(120) NOT NULL,
    source_price_id VARCHAR(120) NOT NULL,
    target_prorated DECIMAL(12,2) NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL,
    credit_amount DECIMAL(12,2) NOT NULL,
    due_amount DECIMAL(12,2) NOT NULL,
    coupon_code VARCHAR(36),
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    quoted_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    charge_id VARCHAR(120) UNIQUE,
    state VARCHAR(30) NOT NULL,
    message VARCHAR(500),
    paid_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_upgrade_adjustment_store ON upgrade_adjustments(store_id, quoted_at);
CREATE INDEX idx_upgrade_adjustment_state ON upgrade_adjustments(state, quoted_at);
ALTER TABLE payment_subscriptions ADD COLUMN upgrade_payment_pending BOOLEAN NOT NULL DEFAULT FALSE;
