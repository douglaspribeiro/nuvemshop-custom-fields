CREATE TABLE dlocal_go_operations (
 id VARCHAR(36) PRIMARY KEY, kind VARCHAR(20) NOT NULL, state VARCHAR(20) NOT NULL,
 store_id BIGINT NOT NULL, environment VARCHAR(20) NOT NULL, subscription_id VARCHAR(120),
 source_plan VARCHAR(30), target_plan VARCHAR(30), source_amount DECIMAL(12,2), target_amount DECIMAL(12,2),
 due_amount DECIMAL(12,2) NOT NULL, source_price_id VARCHAR(120), target_price_id VARCHAR(120),
 source_payment_id VARCHAR(120), invoice_id VARCHAR(120), payment_id VARCHAR(120), remote_id VARCHAR(120),
 checkout_url VARCHAR(2048), period_start TIMESTAMP(6) NULL, period_end TIMESTAMP(6) NULL,
 expires_at TIMESTAMP(6) NULL, message VARCHAR(500), created_at TIMESTAMP(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 INDEX idx_dlocal_operations_store (store_id,created_at), INDEX idx_dlocal_operations_state (kind,state)
);
CREATE TABLE dlocal_go_reversals (
 id VARCHAR(180) PRIMARY KEY, environment VARCHAR(20) NOT NULL, order_id VARCHAR(120) NOT NULL,
 kind VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL, currency VARCHAR(3) NOT NULL,
 amount DECIMAL(12,2) NOT NULL, INDEX idx_dlocal_reversal_order (environment,order_id)
);
