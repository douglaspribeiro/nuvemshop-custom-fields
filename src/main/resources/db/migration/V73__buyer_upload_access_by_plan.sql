-- Upload access now follows Pro/Ultra plans and their quotas directly.
-- Preserve all buyer files, sessions, orders and retention metadata.
DROP TABLE buyer_upload_entitlements;
ALTER TABLE payment_subscriptions DROP COLUMN buyer_upload_offer;
