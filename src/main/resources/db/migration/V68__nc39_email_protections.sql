CREATE TABLE nc39_activation_email_suppressions (
    store_id BIGINT NOT NULL PRIMARY KEY,
    reason VARCHAR(32) NOT NULL,
    suppressed_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE nc39_activation_email_outbox ADD COLUMN unsubscribe_token_hash VARCHAR(64) NULL;
ALTER TABLE nc39_activation_email_outbox ADD COLUMN recipient_hash VARCHAR(64) NULL;
ALTER TABLE nc39_activation_email_outbox ADD COLUMN ses_message_id VARCHAR(200) NULL;
ALTER TABLE nc39_activation_email_outbox ADD CONSTRAINT uk_nc39_unsubscribe_token UNIQUE (unsubscribe_token_hash);

CREATE TABLE nc39_activation_email_feedback (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    INDEX ix_nc39_feedback_store (store_id),
    INDEX ix_nc39_feedback_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
