CREATE TABLE store_email_history (
    id VARCHAR(36) PRIMARY KEY,
    store_id BIGINT NOT NULL,
    category VARCHAR(40) NOT NULL,
    source_key VARCHAR(120) NULL,
    recipient VARCHAR(320) NULL,
    subject VARCHAR(500) NULL,
    body TEXT NULL,
    state VARCHAR(30) NOT NULL,
    attempted_at TIMESTAMP(6) NOT NULL,
    accepted_at TIMESTAMP(6) NULL,
    UNIQUE KEY uk_store_email_source (source_key),
    INDEX ix_store_email_history (store_id, attempted_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Retain only facts recorded at send time. Never reconstruct old copy or recipients.
INSERT INTO store_email_history (id,store_id,category,source_key,state,attempted_at,accepted_at)
SELECT UUID(),store_id,CONCAT('ACTIVATION_',step),CONCAT('activation:',id),'ACCEPTED',sent_at,sent_at
FROM nc39_activation_email_outbox WHERE sent_at IS NOT NULL;
INSERT INTO store_email_history (id,store_id,category,source_key,state,attempted_at,accepted_at)
SELECT UUID(),c.store_id,CONCAT('WINBACK_',e.step),CONCAT('winback:',e.id),'ACCEPTED',e.sent_at,e.sent_at
FROM winback_emails e JOIN winback_campaigns c ON c.id=e.campaign_id WHERE e.sent_at IS NOT NULL;
INSERT INTO store_email_history (id,store_id,category,source_key,state,attempted_at,accepted_at)
SELECT UUID(),store_id,'RENEWAL_FAILURE',CONCAT('renewal:',id),'ACCEPTED',completed_at,completed_at
FROM renewal_failure_emails WHERE state='SENT' AND completed_at IS NOT NULL;
INSERT INTO store_email_history (id,store_id,category,source_key,state,attempted_at,accepted_at)
SELECT UUID(),store_id,'CONFIGURATION_PAUSE',CONCAT('pause:',id),'ACCEPTED',completed_at,completed_at
FROM configuration_pause_emails WHERE state='SENT' AND completed_at IS NOT NULL;
