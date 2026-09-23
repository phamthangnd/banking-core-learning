-- Phase 05: money movement.
--
-- Rule this table exists to enforce (CLAUDE.md section 3): a balance is never changed without a
-- business transaction recorded in the same database transaction. The row and the new balance
-- commit together or neither does.

CREATE SEQUENCE transaction_reference_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE transactions (
    id                   UUID           NOT NULL,
    -- Customer-facing reference, printed on statements and quoted in support calls.
    reference            VARCHAR(30)    NOT NULL,
    transaction_type     VARCHAR(20)    NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    currency             CHAR(3)        NOT NULL,
    -- Always positive; the type and the account columns say which way the money went.
    amount               NUMERIC(19, 4) NOT NULL,

    -- Where the money came from and went to. A deposit has no source, a withdrawal no target.
    source_account_id    UUID,
    target_account_id    UUID,

    -- Balances after the movement, captured at posting time. A statement must show what the
    -- balance was then, not what it is now; recomputing it later from a running total is how
    -- statements start disagreeing with the ledger.
    source_balance_after NUMERIC(19, 4),
    target_balance_after NUMERIC(19, 4),

    description          VARCHAR(255),
    -- Why a FAILED transaction failed. Never contains data the customer may not see.
    failure_reason       VARCHAR(255),

    occurred_at          TIMESTAMPTZ    NOT NULL,
    posted_at            TIMESTAMPTZ,
    created_at           TIMESTAMPTZ    NOT NULL,

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_source FOREIGN KEY (source_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_target FOREIGN KEY (target_account_id) REFERENCES accounts (id),

    CONSTRAINT ck_transactions_type CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT ck_transactions_status CHECK (status IN ('PENDING', 'POSTED', 'FAILED', 'REVERSED')),
    CONSTRAINT ck_transactions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),

    -- The shape of each transaction type, enforced by the database so a malformed record cannot
    -- exist at all: a deposit credits one account, a withdrawal debits one, a transfer moves
    -- between two different accounts.
    CONSTRAINT ck_transactions_shape CHECK (
        (transaction_type = 'DEPOSIT'    AND source_account_id IS NULL     AND target_account_id IS NOT NULL) OR
        (transaction_type = 'WITHDRAWAL' AND source_account_id IS NOT NULL AND target_account_id IS NULL) OR
        (transaction_type = 'TRANSFER'   AND source_account_id IS NOT NULL AND target_account_id IS NOT NULL
                                         AND source_account_id <> target_account_id)
    ),

    -- posted_at records that the money actually moved, so it stays set after a reversal:
    -- a reversed transaction was posted, and its statement line has to keep its timestamp.
    -- PENDING and FAILED never have one, because nothing moved.
    CONSTRAINT ck_transactions_posted_at
        CHECK ((status IN ('POSTED', 'REVERSED')) = (posted_at IS NOT NULL))
);

CREATE UNIQUE INDEX ux_transactions_reference ON transactions (reference);

-- Account statements: "this account's transactions, newest first".
CREATE INDEX ix_transactions_source_occurred ON transactions (source_account_id, occurred_at DESC);
CREATE INDEX ix_transactions_target_occurred ON transactions (target_account_id, occurred_at DESC);
CREATE INDEX ix_transactions_status_occurred ON transactions (status, occurred_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Immutability
-- ---------------------------------------------------------------------------------------------
-- A posted financial record is never edited; a correction is a new compensating transaction
-- (CLAUDE.md section 3). Application code already refuses to change one, but the ledger's
-- integrity should not depend on every future caller remembering that — so the database refuses
-- too, including for an ad-hoc UPDATE run by hand.
--
-- Only the status may move forward, and only into a state the lifecycle allows.

CREATE OR REPLACE FUNCTION transactions_forbid_mutation() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.id <> OLD.id
       OR NEW.reference <> OLD.reference
       OR NEW.transaction_type <> OLD.transaction_type
       OR NEW.currency <> OLD.currency
       OR NEW.amount <> OLD.amount
       OR NEW.source_account_id IS DISTINCT FROM OLD.source_account_id
       OR NEW.target_account_id IS DISTINCT FROM OLD.target_account_id
       OR NEW.occurred_at <> OLD.occurred_at
       OR NEW.created_at <> OLD.created_at
    THEN
        RAISE EXCEPTION 'transaction % is immutable; post a compensating transaction instead', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    IF OLD.status IN ('FAILED', 'REVERSED') AND NEW.status <> OLD.status THEN
        RAISE EXCEPTION 'transaction % is in terminal state %', OLD.id, OLD.status
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_forbid_mutation
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION transactions_forbid_mutation();

-- Deleting a financial record is never correct (CLAUDE.md section 5).
CREATE OR REPLACE FUNCTION transactions_forbid_delete() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'financial records must not be deleted'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_forbid_delete
    BEFORE DELETE ON transactions
    FOR EACH ROW EXECUTE FUNCTION transactions_forbid_delete();

COMMENT ON TABLE transactions IS 'Immutable record of every money movement. Corrections are compensating transactions.';
