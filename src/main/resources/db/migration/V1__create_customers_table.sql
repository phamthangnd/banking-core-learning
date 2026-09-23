-- Phase 02: the customer table.
--
-- Flyway owns the schema. Hibernate runs with ddl-auto=validate and may never create or alter
-- a table (CLAUDE.md section 5): a schema change is a reviewed, versioned, repeatable artifact,
-- not a side effect of starting an application.
--
-- A migration that has been applied is never edited afterwards; corrections ship as a new
-- versioned file, exactly like a reversal corrects a posted transaction.

CREATE TABLE customers (
    id            UUID         NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    -- Stored already normalised (trimmed, lower case) by the domain model, so the unique
    -- index below is a true case-insensitive uniqueness guarantee.
    email         VARCHAR(255) NOT NULL,
    phone_number  VARCHAR(20)  NOT NULL,
    date_of_birth DATE         NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    -- Optimistic locking counter used by JPA's @Version. Phase 06 relies on it to detect
    -- concurrent modification instead of silently overwriting another transaction's work.
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT ck_customers_status CHECK (status IN ('ACTIVE', 'CLOSED'))
);

-- Uniqueness of an email is a data integrity rule, so the database enforces it.
-- The service check remains as a fast, friendly path; the index is what makes the rule true
-- under concurrency, where two requests can both pass an application-level check.
CREATE UNIQUE INDEX ux_customers_email ON customers (email);

-- Default listing order is newest/oldest first, usually filtered by status.
-- A composite index on (status, created_at) serves both the filter and the sort.
CREATE INDEX ix_customers_status_created_at ON customers (status, created_at);

-- Name search is case-insensitive, so the expression must match the query's expression.
-- Note the honest limitation: a leading-wildcard LIKE ('%text%') cannot use a B-tree index and
-- still forces a scan. Real text search (trigram or full-text) is a Phase 08 concern.
CREATE INDEX ix_customers_full_name_lower ON customers (LOWER(full_name));

COMMENT ON TABLE customers IS 'Bank customers. Rows are closed, never deleted.';
