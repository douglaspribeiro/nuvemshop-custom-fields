ALTER TABLE buyer_uploads
    ADD COLUMN storage_provider VARCHAR(30) NOT NULL DEFAULT 'AWS_S3';

ALTER TABLE stores
    ADD COLUMN buyer_upload_storage_provider VARCHAR(30) NULL;
