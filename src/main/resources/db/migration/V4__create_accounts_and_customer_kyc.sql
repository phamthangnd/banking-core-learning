-- Phase 04: customer profile (KYC, avatar) and bank accounts.

-- ---------------------------------------------------------------------------------------------
-- Customer profile
-- ---------------------------------------------------------------------------------------------

ALTER TABLE customers
    -- Know Your Customer state. A bank may not open an account for an unverified customer, so
    -- this column gates account activation rather than being decoration.
    ADD COLUMN kyc_status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN kyc_reviewed_at TIMESTAMPTZ,
    -- Reference to a file in the file module (Phase 07). Deliberately a bare id and not a URL:
    -- storage location is the file module's business, and a URL in this table would freeze it.
    ADD COLUMN avatar_file_id  UUID;

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_kyc_status CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED'));

-- Verified customers are what the account-opening path filters on.
CREATE INDEX ix_customers_kyc_status ON customers (kyc_status);

-- ---------------------------------------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------------------------------------

-- Account numbers are handed out from a sequence rather than generated randomly and retried:
-- the database settles uniqueness under concurrency, and a sequence never collides.
CREATE SEQUENCE account_number_seq START WITH 1000001 INCREMENT BY 1;

CREATE TABLE accounts (
    id              UUID         NOT NULL,
    -- Digits only: bank prefix, sequence, and a Luhn check digit (see AccountNumberGenerator).
    account_number  VARCHAR(20)  NOT NULL,
    customer_id     UUID         NOT NULL,
    account_type    VARCHAR(20)  NOT NULL,
    -- Currency is explicit and lives next to the amount; an amount without one is meaningless
    -- (CLAUDE.md section 3).
    currency        CHAR(3)      NOT NULL,
    -- NUMERIC, never a floating-point type. 19 digits with 4 decimals covers minor units finer
    -- than cents (interest accrual, FX) without ever rounding by accident.
    balance         NUMERIC(19, 4) NOT NULL DEFAULT 0,
    -- How far below zero this account may go. Zero for ordinary accounts; a positive value is
    -- an explicitly granted overdraft.
    overdraft_limit NUMERIC(19, 4) NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL,
    opened_at       TIMESTAMPTZ  NOT NULL,
    activated_at    TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id),

    CONSTRAINT ck_accounts_status CHECK (status IN ('PENDING', 'ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_accounts_type CHECK (account_type IN ('CHECKING', 'SAVINGS', 'TERM_DEPOSIT')),
    CONSTRAINT ck_accounts_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_accounts_overdraft_not_negative CHECK (overdraft_limit >= 0),

    -- The core banking invariant, enforced by the database and not only by application code:
    -- a balance may only go below zero as far as an explicitly granted overdraft allows.
    -- Phase 05 moves money; this constraint is what makes a bug there fail loudly instead of
    -- quietly creating money.
    CONSTRAINT ck_accounts_balance_within_overdraft CHECK (balance >= -overdraft_limit),

    -- A closed account must record when it was closed, and only a closed one may have that date.
    CONSTRAINT ck_accounts_closed_at CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),

    -- Closing an account with money still on it would strand the money.
    CONSTRAINT ck_accounts_closed_balance_is_zero CHECK (status <> 'CLOSED' OR balance = 0)
);

CREATE UNIQUE INDEX ux_accounts_number ON accounts (account_number);

-- "All accounts of this customer", usually filtered by status: the composite index serves both.
CREATE INDEX ix_accounts_customer_status ON accounts (customer_id, status);

-- Back-office listings filter by status and sort by opening date.
CREATE INDEX ix_accounts_status_opened_at ON accounts (status, opened_at);

COMMENT ON TABLE accounts IS 'Bank accounts. Closed, never deleted; balances are NUMERIC, never floating point.';
COMMENT ON COLUMN accounts.overdraft_limit IS 'Maximum permitted negative balance, as a positive number.';
