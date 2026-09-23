-- The notification service owns this table, in its own database.
--
-- No foreign key to users or accounts, because those tables live in another service's database
-- and a foreign key across a service boundary is a shared database wearing a disguise. The ids
-- are stored as plain values and are meaningless here beyond addressing and linking.

CREATE TABLE notifications (
    id            UUID          NOT NULL,
    user_id       UUID          NOT NULL,
    type          VARCHAR(40)   NOT NULL,
    title         VARCHAR(150)  NOT NULL,
    body          VARCHAR(1000) NOT NULL,
    resource_type VARCHAR(40),
    resource_id   UUID,
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL,

    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;

-- Consumer deduplication. At-least-once delivery means this service will see the same event
-- again; the primary key is what makes the second delivery do nothing.
CREATE TABLE processed_events (
    event_id     UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);
