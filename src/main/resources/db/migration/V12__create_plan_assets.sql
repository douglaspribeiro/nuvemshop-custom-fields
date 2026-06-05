CREATE TABLE plan_assets (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_type           VARCHAR(30)    NOT NULL,
    display_name        VARCHAR(120)   NOT NULL,
    description         VARCHAR(500),
    billing_external_id VARCHAR(80),
    currency            VARCHAR(3)     NOT NULL,
    amount              DECIMAL(10,2)  NOT NULL,
    product_limit       BIGINT         NOT NULL,
    field_limit         BIGINT         NOT NULL,
    effective_from      DATE           NOT NULL,
    effective_until     DATE,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_plan_assets_type_effective (plan_type, active, effective_from, effective_until),
    INDEX ix_plan_assets_billing_external_id (billing_external_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO plan_assets (
    plan_type,
    display_name,
    description,
    billing_external_id,
    currency,
    amount,
    product_limit,
    field_limit,
    effective_from,
    active
) VALUES
    ('FREE', 'Free', 'Campos Personalizados Free', NULL, 'BRL', 0.00, 1, 1, '2026-01-01', TRUE),
    ('PREMIUM', 'Premium', 'Campos Personalizados Premium', 'PREMIUM', 'BRL', 9.99, 10, 3, '2026-01-01', TRUE),
    ('PREMIUM_PLUS', 'Premium Plus', 'Campos Personalizados Premium Plus', 'PREMIUM_PLUS', 'BRL', 19.99, -1, -1, '2026-01-01', TRUE);
