-- Phase 07: stored files, the notification inbox and the audit trail.

-- ---------------------------------------------------------------------------------------------
-- Files
-- ---------------------------------------------------------------------------------------------
-- The bytes live in object storage; this table is the metadata and the permission boundary.
-- Storing the content in PostgreSQL would bloat the database, its backups and its replication
-- stream with data that never needs a transaction.

CREATE TABLE stored_files (
    id             UUID         NOT NULL,
    -- Key inside the bucket. Generated, never the name the user typed: an uploaded filename is
    -- attacker-controlled and would otherwise decide where the object lands.
    storage_key    VARCHAR(255) NOT NULL,
    -- Kept only to offer it back on download, and always sanitised.
    original_name  VARCHAR(255) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    -- SHA-256 of the content: deduplication, and evidence that what comes back is what went in.
    checksum       VARCHAR(64)  NOT NULL,
    category       VARCHAR(30)  NOT NULL,
    -- Who uploaded it. Access checks start here.
    uploaded_by    UUID         NOT NULL,
    -- Optional owner, so a customer's documents can be found and access-checked as a group.
    customer_id    UUID,
    created_at     TIMESTAMPTZ  NOT NULL,
    deleted_at     TIMESTAMPTZ,

    CONSTRAINT pk_stored_files PRIMARY KEY (id),
    CONSTRAINT fk_stored_files_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT fk_stored_files_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_stored_files_category CHECK (category IN ('AVATAR', 'KYC_DOCUMENT', 'STATEMENT', 'OTHER')),
    CONSTRAINT ck_stored_files_size_positive CHECK (size_bytes > 0)
);

CREATE UNIQUE INDEX ux_stored_files_storage_key ON stored_files (storage_key);
CREATE INDEX ix_stored_files_customer ON stored_files (customer_id, created_at DESC);
CREATE INDEX ix_stored_files_checksum ON stored_files (checksum);

-- ---------------------------------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------------------------------

CREATE TABLE notifications (
    id         UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(150) NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    -- What the notification is about, so a client can link to it. No amounts, no balances:
    -- a notification is a pointer, not a copy of the data.
    resource_type VARCHAR(40),
    resource_id   UUID,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- "My unread notifications, newest first" is the query the inbox runs constantly.
CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;

-- ---------------------------------------------------------------------------------------------
-- Audit trail
-- ---------------------------------------------------------------------------------------------
-- Who did what, to which resource, when, and whether it worked. Append-only: an audit trail that
-- can be edited is not an audit trail.

CREATE TABLE audit_events (
    id            UUID         NOT NULL,
    -- Null for an unauthenticated action such as a failed login attempt.
    actor_id      UUID,
    actor_name    VARCHAR(100),
    action        VARCHAR(60)  NOT NULL,
    resource_type VARCHAR(40)  NOT NULL,
    resource_id   VARCHAR(100),
    outcome       VARCHAR(20)  NOT NULL,
    -- Correlation id of the request, so an audit entry leads straight to its log lines.
    trace_id      VARCHAR(64),
    -- Small structured detail. Never credentials, never balances, never full account data.
    detail        VARCHAR(500),
    occurred_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_audit_events PRIMARY KEY (id),
    CONSTRAINT ck_audit_events_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX ix_audit_events_occurred ON audit_events (occurred_at DESC);
CREATE INDEX ix_audit_events_actor ON audit_events (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_events_resource ON audit_events (resource_type, resource_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION audit_events_forbid_change() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit events are append-only'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_forbid_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_forbid_change();

CREATE TRIGGER trg_audit_events_forbid_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_forbid_change();

COMMENT ON TABLE audit_events IS 'Append-only record of who did what to which resource.';
