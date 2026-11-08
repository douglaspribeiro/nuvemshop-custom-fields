-- Explicit entitlement: no existing subscription acquires uploads from its plan name.
ALTER TABLE payment_subscriptions ADD COLUMN buyer_upload_offer VARCHAR(40) NULL;

CREATE TABLE buyer_upload_entitlements (
    store_id BIGINT NOT NULL PRIMARY KEY,
    offer_code VARCHAR(40) NOT NULL,
    quota_bytes BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_buyer_upload_entitlement_store FOREIGN KEY (store_id) REFERENCES stores(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Deliberately independent of fields/products: removing configuration must not
-- remove artwork already needed to produce an order. No public object URLs.
CREATE TABLE buyer_uploads (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    field_id BIGINT NOT NULL,
    session_hash VARCHAR(64) NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    size_bytes BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    order_id BIGINT NULL,
    order_item_id BIGINT NULL,
    order_created_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    delete_reason VARCHAR(20) NULL,
    original_name VARCHAR(180) NULL,
    content_type VARCHAR(40) NULL,
    bucket VARCHAR(120) NULL,
    object_key VARCHAR(255) NULL,
    retry_at TIMESTAMP(6) NULL,
    delete_attempts INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_buyer_upload_request UNIQUE (store_id, session_hash, request_id),
    INDEX idx_buyer_upload_usage (store_id, state),
    INDEX idx_buyer_upload_attempts (store_id, created_at),
    INDEX idx_buyer_upload_expiry (state, expires_at),
    INDEX idx_buyer_upload_order (store_id, order_id, order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE buyer_upload_sessions (
    session_hash VARCHAR(64) NOT NULL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    INDEX idx_buyer_session_store (store_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
