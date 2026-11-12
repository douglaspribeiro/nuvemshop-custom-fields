ALTER TABLE feature_requests
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE feature_request_updates (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    submission_token VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    email_state VARCHAR(20) NOT NULL,
    email_sent_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_feature_request_update_token UNIQUE (submission_token),
    CONSTRAINT fk_feature_request_update_request FOREIGN KEY (request_id) REFERENCES feature_requests(id) ON DELETE CASCADE,
    INDEX idx_feature_request_update_history (request_id,created_at,id),
    INDEX idx_feature_request_update_email (email_state,created_at)
);
