ALTER TABLE personalization_rules
    ADD COLUMN plan_paused BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE personalization_rules ADD COLUMN plan_paused_at TIMESTAMP(6) NULL;
ALTER TABLE personalization_rules ADD COLUMN plan_eligibility_evidence TEXT NULL;
ALTER TABLE personalization_rules ADD COLUMN plan_eligibility_evaluated_at TIMESTAMP(6) NULL;

CREATE TABLE configuration_pause_emails (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paused_products TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    INDEX ix_configuration_pause_email_state (state, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
