CREATE TABLE support_tickets (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id        BIGINT       NOT NULL,
    subject         VARCHAR(160) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_message_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_support_tickets_store_updated (store_id, updated_at),
    INDEX ix_support_tickets_status_message (status, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE support_messages (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT       NOT NULL,
    author_type VARCHAR(20)  NOT NULL,
    message     TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_support_messages_ticket
        FOREIGN KEY (ticket_id) REFERENCES support_tickets (id) ON DELETE CASCADE,
    INDEX ix_support_messages_ticket_created (ticket_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
