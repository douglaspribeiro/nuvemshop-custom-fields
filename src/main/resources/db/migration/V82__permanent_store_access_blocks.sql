CREATE TABLE store_access_blocks (
    store_id BIGINT NOT NULL PRIMARY KEY,
    blocked_at TIMESTAMP(6) NOT NULL
);
ALTER TABLE stores ADD COLUMN access_blocked_at TIMESTAMP(6) NULL;
