ALTER TABLE product_complement_offers ADD COLUMN show_image BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE product_complement_offers ADD COLUMN background_color VARCHAR(7);
ALTER TABLE product_complement_offers ADD COLUMN text_color VARCHAR(7);
ALTER TABLE product_complement_offers ADD COLUMN show_border BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE product_complement_offers ADD COLUMN rounded_corners BOOLEAN NOT NULL DEFAULT TRUE;
