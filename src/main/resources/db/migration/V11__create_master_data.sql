-- Phase 08: master data.
--
-- Reference data that a bank changes without a release: branches, document types, transaction
-- categories. Distinct from the role and permission catalogue, which is part of the application's
-- contract and therefore belongs in a migration.
--
-- One table with a type discriminator rather than a table per kind: every one of these is a code,
-- a label and a sort order, and a dozen near-identical tables would each need their own endpoint,
-- repository and cache for no benefit.

CREATE TABLE master_data (
    id          UUID         NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    code        VARCHAR(40)  NOT NULL,
    label       VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    -- Presentation order, so a client never has to sort by label and get it wrong per locale.
    sort_order  INT          NOT NULL DEFAULT 0,
    -- Retired entries stay: existing records may still reference them.
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_master_data PRIMARY KEY (id),
    CONSTRAINT ck_master_data_code CHECK (code ~ '^[A-Z0-9_]+$')
);

CREATE UNIQUE INDEX ux_master_data_type_code ON master_data (type, code);
CREATE INDEX ix_master_data_type_active ON master_data (type, active, sort_order);

INSERT INTO master_data (id, type, code, label, description, sort_order, active, created_at, updated_at) VALUES
    ('c0000000-0000-4000-8000-000000000001', 'BRANCH', 'HN01', 'Ha Noi - Head Office', NULL, 1, TRUE, now(), now()),
    ('c0000000-0000-4000-8000-000000000002', 'BRANCH', 'SG01', 'Ho Chi Minh City - District 1', NULL, 2, TRUE, now(), now()),
    ('c0000000-0000-4000-8000-000000000003', 'BRANCH', 'DN01', 'Da Nang', NULL, 3, TRUE, now(), now()),

    ('c0000000-0000-4000-8000-000000000011', 'DOCUMENT_TYPE', 'NATIONAL_ID', 'National identity card', NULL, 1, TRUE, now(), now()),
    ('c0000000-0000-4000-8000-000000000012', 'DOCUMENT_TYPE', 'PASSPORT', 'Passport', NULL, 2, TRUE, now(), now()),
    ('c0000000-0000-4000-8000-000000000013', 'DOCUMENT_TYPE', 'DRIVING_LICENCE', 'Driving licence', NULL, 3, TRUE, now(), now()),

    ('c0000000-0000-4000-8000-000000000021', 'CURRENCY', 'VND', 'Vietnamese dong', 'Minor unit: 0 decimals in practice', 1, TRUE, now(), now()),
    ('c0000000-0000-4000-8000-000000000022', 'CURRENCY', 'USD', 'US dollar', 'Minor unit: 2 decimals', 2, TRUE, now(), now()),
    ('c0000000-0000-4000-8000-000000000023', 'CURRENCY', 'EUR', 'Euro', 'Minor unit: 2 decimals', 3, TRUE, now(), now()),

    ('c0000000-0000-4000-8000-000000000031', 'TRANSACTION_CATEGORY', 'SALARY', 'Salary', NULL, 1, TRUE, now(), now()),
    ('c0000000-0000-4000-8000-000000000032', 'TRANSACTION_CATEGORY', 'RENT', 'Rent', NULL, 2, TRUE, now(), now()),
    ('c0000000-0000-4000-8000-000000000033', 'TRANSACTION_CATEGORY', 'UTILITIES', 'Utilities', NULL, 3, TRUE, now(), now()),
    ('c0000000-0000-4000-8000-000000000034', 'TRANSACTION_CATEGORY', 'TRANSFER', 'Transfer', NULL, 4, TRUE, now(), now());

COMMENT ON TABLE master_data IS 'Editable reference data: branches, document types, currencies, categories.';
