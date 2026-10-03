CREATE TABLE feature_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    plan_at_submission VARCHAR(30) NOT NULL,
    submission_token VARCHAR(36) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL,
    discord_sent_at TIMESTAMP(6) NULL,
    next_notification_at TIMESTAMP(6) NOT NULL,
    notification_attempts INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_feature_request_store ON feature_requests(store_id,created_at);
CREATE INDEX idx_feature_request_notification ON feature_requests(discord_sent_at,next_notification_at);
