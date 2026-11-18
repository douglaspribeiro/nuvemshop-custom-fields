CREATE TABLE efi_pix_recurrences (
    request_id CHAR(36) PRIMARY KEY,
    store_id BIGINT NOT NULL,
    plan_type VARCHAR(32) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    first_debit DATE NOT NULL,
    debit_count INT NOT NULL,
    payment_id VARCHAR(160),
    payer_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_efi_pix_payment (environment, payment_id),
    INDEX idx_efi_pix_store (store_id, created_at),
    CONSTRAINT fk_efi_pix_store FOREIGN KEY (store_id) REFERENCES stores(store_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE efi_pix_debits (
    request_id CHAR(36) NOT NULL,
    end_to_end_id VARCHAR(64) NOT NULL,
    operation_date DATE NOT NULL,
    provider_status VARCHAR(32) NOT NULL,
    PRIMARY KEY (request_id, end_to_end_id),
    CONSTRAINT fk_efi_pix_recurrence FOREIGN KEY (request_id) REFERENCES efi_pix_recurrences(request_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
