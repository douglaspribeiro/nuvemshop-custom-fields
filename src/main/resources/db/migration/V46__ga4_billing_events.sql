ALTER TABLE payment_subscriptions ADD COLUMN analytics_client_id VARCHAR(64) NULL;
ALTER TABLE payment_subscriptions ADD COLUMN analytics_session_id VARCHAR(20) NULL;
ALTER TABLE payment_subscriptions ADD COLUMN analytics_first_payment_id VARCHAR(120) NULL;
ALTER TABLE upgrade_adjustments ADD COLUMN analytics_client_id VARCHAR(64) NULL;
ALTER TABLE upgrade_adjustments ADD COLUMN analytics_session_id VARCHAR(20) NULL;
CREATE TABLE ga4_outbox (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 event_key VARCHAR(180) NOT NULL,
 payload TEXT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 next_attempt_at DATETIME(6) NOT NULL,
 delivered_at DATETIME(6) NULL,
 expired_at DATETIME(6) NULL,
 attempts INT NOT NULL DEFAULT 0,
 CONSTRAINT uk_ga4_event_key UNIQUE (event_key),
 INDEX ix_ga4_due (delivered_at, expired_at, next_attempt_at)
);
