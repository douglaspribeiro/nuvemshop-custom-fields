CREATE TABLE merchant_interface_preferences (
    store_id BIGINT NOT NULL PRIMARY KEY,
    interface_choice VARCHAR(16) NOT NULL,
    CONSTRAINT fk_merchant_interface_store FOREIGN KEY (store_id) REFERENCES stores(store_id) ON DELETE CASCADE
);
