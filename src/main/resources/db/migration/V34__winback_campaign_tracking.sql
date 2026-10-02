CREATE TABLE winback_campaigns (
    id VARCHAR(36) PRIMARY KEY,
    store_id BIGINT NOT NULL,
    uninstalled_at TIMESTAMP(6) NOT NULL,
    reinstalled_at TIMESTAMP(6) NULL,
    opted_out_at TIMESTAMP(6) NULL,
    reason VARCHAR(40) NULL,
    response VARCHAR(2000) NULL,
    responded_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_campaign_departure UNIQUE (store_id, uninstalled_at),
    INDEX idx_campaign_departure (uninstalled_at),
    CONSTRAINT fk_campaign_store FOREIGN KEY (store_id) REFERENCES stores(store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE winback_emails (
    id VARCHAR(36) PRIMARY KEY,
    campaign_id VARCHAR(36) NOT NULL,
    step VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    sent_at TIMESTAMP(6) NULL,
    ses_message_id VARCHAR(200) NULL,
    CONSTRAINT uk_campaign_email_step UNIQUE (campaign_id, step),
    CONSTRAINT fk_email_campaign FOREIGN KEY (campaign_id) REFERENCES winback_campaigns(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE winback_email_events (
    id VARCHAR(64) PRIMARY KEY,
    email_id VARCHAR(36) NOT NULL,
    type VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    target VARCHAR(40) NULL,
    CONSTRAINT fk_event_email FOREIGN KEY (email_id) REFERENCES winback_emails(id),
    INDEX idx_event_time (email_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- List existing retained uninstallations without automatically mailing them.
INSERT INTO winback_campaigns (id, store_id, uninstalled_at)
SELECT UUID(), store_id, uninstalled_at FROM stores WHERE uninstalled_at IS NOT NULL;
