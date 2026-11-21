CREATE TABLE support_attachments (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id  BIGINT       NOT NULL,
    filename    VARCHAR(240) NOT NULL,
    contents    MEDIUMBLOB   NOT NULL,
    CONSTRAINT fk_support_attachments_message
        FOREIGN KEY (message_id) REFERENCES support_messages (id) ON DELETE CASCADE,
    INDEX ix_support_attachments_message (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
