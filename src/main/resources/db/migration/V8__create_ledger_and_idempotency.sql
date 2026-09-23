-- Phase 06: the double-entry ledger and idempotency keys.

-- ---------------------------------------------------------------------------------------------
-- Ledger
-- ---------------------------------------------------------------------------------------------
-- Every money movement writes balanced entries: the sum of debits equals the sum of credits, per
-- transaction and therefore across the whole ledger (CLAUDE.md section 3).
--
-- A deposit or withdrawal has one customer leg and one bank leg. The bank leg has no account row
-- — it is the bank's own cash or clearing position — so an entry names either an account or a
-- system account, never both and never neither. Without that second leg a deposit would be a
-- single entry and the invariant would be false by construction.

CREATE TABLE ledger_entries (
    id             UUID           NOT NULL,
    transaction_id UUID           NOT NULL,
    -- Position within its transaction; makes the pair orderable and the uniqueness checkable.
    entry_index    INT            NOT NULL,

    -- Exactly one of these two is set.
    account_id     UUID,
    system_account VARCHAR(20),

    direction      VARCHAR(6)     NOT NULL,
    currency       CHAR(3)        NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    -- Balance of the customer account after this entry; null for a system leg, which has no
    -- account row to carry a balance.
    balance_after  NUMERIC(19, 4),
    created_at     TIMESTAMPTZ    NOT NULL,

    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entries_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES accounts (id),

    CONSTRAINT ck_ledger_entries_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entries_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_ledger_entries_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ledger_entries_system_account CHECK (system_account IN ('CASH', 'CLEARING')),
    -- One leg, one owner: a customer account or the bank, never both.
    CONSTRAINT ck_ledger_entries_one_side CHECK ((account_id IS NULL) <> (system_account IS NULL)),
    CONSTRAINT ck_ledger_entries_balance_after CHECK ((account_id IS NULL) = (balance_after IS NULL))
);

CREATE UNIQUE INDEX ux_ledger_entries_transaction_index ON ledger_entries (transaction_id, entry_index);
CREATE INDEX ix_ledger_entries_account ON ledger_entries (account_id, created_at DESC);

-- The ledger is append-only. Same reasoning as the transactions table: an entry that can be
-- edited is not evidence, and a balance derived from editable entries is not a balance.
CREATE OR REPLACE FUNCTION ledger_entries_forbid_change() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger entries are append-only'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_forbid_update
    BEFORE UPDATE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_entries_forbid_change();

CREATE TRIGGER trg_ledger_entries_forbid_delete
    BEFORE DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_entries_forbid_change();

-- ---------------------------------------------------------------------------------------------
-- Idempotency
-- ---------------------------------------------------------------------------------------------
-- A client that retries after a timeout must not move the money twice. The key is supplied by
-- the client and stored with a fingerprint of the request, so the same key with a different body
-- is rejected rather than silently answered with somebody else's transaction.

CREATE TABLE idempotency_keys (
    id                  UUID         NOT NULL,
    scope               VARCHAR(50)  NOT NULL,
    idempotency_key     VARCHAR(100) NOT NULL,
    -- SHA-256 of the canonical request. Same key + same request = replay; different request is
    -- a client bug and must be told so.
    request_fingerprint VARCHAR(64)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    transaction_id      UUID,
    created_at          TIMESTAMPTZ  NOT NULL,
    completed_at        TIMESTAMPTZ,

    CONSTRAINT pk_idempotency_keys PRIMARY KEY (id),
    CONSTRAINT fk_idempotency_keys_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT ck_idempotency_keys_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_idempotency_keys_completed CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);

-- The unique index is the mechanism, not an optimisation: two concurrent retries both try to
-- insert, and the database decides which one proceeds. An application-level "check then insert"
-- has a race between the two steps.
CREATE UNIQUE INDEX ux_idempotency_keys_scope_key ON idempotency_keys (scope, idempotency_key);

COMMENT ON TABLE ledger_entries IS 'Append-only double-entry ledger; debits equal credits per transaction.';
COMMENT ON TABLE idempotency_keys IS 'One key, at most one financial effect.';
