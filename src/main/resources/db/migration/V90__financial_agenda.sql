CREATE TABLE financial_entries (
 entry_key VARCHAR(64) PRIMARY KEY,
 identity_hash VARCHAR(64) NOT NULL UNIQUE,
 provider VARCHAR(30) NOT NULL,
 account_key VARCHAR(80) NOT NULL,
 environment VARCHAR(20) NOT NULL,
 stage VARCHAR(20) NOT NULL,
 source_kind VARCHAR(30) NOT NULL,
 entry_date DATE NULL,
 currency VARCHAR(3) NULL,
 certainty VARCHAR(20) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 document LONGTEXT NOT NULL,
 observed_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 INDEX idx_financial_agenda (environment,stage,entry_date),
 INDEX idx_financial_account (provider,account_key,environment)
);
CREATE TABLE financial_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 entry_key VARCHAR(64) NOT NULL,
 action VARCHAR(40) NOT NULL,
 operator_name VARCHAR(120) NOT NULL,
 evidence VARCHAR(2000) NOT NULL,
 before_document LONGTEXT NULL,
 after_document LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 INDEX idx_financial_audit (entry_key,id)
);
CREATE TABLE financial_rules (
 rule_id VARCHAR(64) PRIMARY KEY,
 document LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE financial_links (
 payout_key VARCHAR(64) NOT NULL,
 release_key VARCHAR(64) NOT NULL,
 amount DECIMAL(19,6) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 evidence VARCHAR(2000) NOT NULL,
 operator_name VARCHAR(120) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 PRIMARY KEY (payout_key,release_key),
 FOREIGN KEY (payout_key) REFERENCES financial_entries(entry_key),
 FOREIGN KEY (release_key) REFERENCES financial_entries(entry_key)
);
CREATE TABLE financial_sync (
 scope_key VARCHAR(190) PRIMARY KEY,
 provider VARCHAR(30) NOT NULL,
 account_key VARCHAR(80) NOT NULL,
 environment VARCHAR(20) NOT NULL,
 attempted_at TIMESTAMP(6) NOT NULL,
 succeeded_at TIMESTAMP(6) NULL,
 coverage VARCHAR(2000) NOT NULL,
 error_message VARCHAR(500) NULL
);
CREATE TABLE financial_drafts (
 draft_id VARCHAR(64) PRIMARY KEY,
 draft_kind VARCHAR(30) NOT NULL,
 document LONGTEXT NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL,
 INDEX idx_financial_draft_expiry (expires_at)
);
