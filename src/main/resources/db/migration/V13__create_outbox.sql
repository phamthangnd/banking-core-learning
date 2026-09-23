-- Phase 09: the transactional outbox.
--
-- The problem it solves: a transfer must both commit to the database and publish an event. Two
-- systems, no shared transaction. Publishing before the commit can announce something that then
-- rolls back; publishing after it can lose the event if the process dies in between. Neither is
-- acceptable for money.
--
-- The outbox makes it one transaction: the event row is written alongside the business change, so
-- it commits or rolls back with it. A separate publisher then reads unsent rows and pushes them
-- to Kafka, retrying until it succeeds. That yields at-least-once delivery, which is why every
-- consumer has to be idempotent.

CREATE TABLE outbox_events (
    id             UUID         NOT NULL,
    -- Kafka topic the event belongs on.
    topic          VARCHAR(100) NOT NULL,
    -- Partition key: events about the same aggregate keep their order.
    message_key    VARCHAR(100) NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    payload        TEXT         NOT NULL,
    -- Correlation id of the request that produced it, carried into the consumer's logs.
    trace_id       VARCHAR(64),
    status         VARCHAR(20)  NOT NULL,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- The publisher's query: oldest unsent first. A partial index keeps it cheap as the published
-- rows accumulate, because they are not in the index at all.
CREATE INDEX ix_outbox_events_pending ON outbox_events (created_at) WHERE status = 'PENDING';
CREATE INDEX ix_outbox_events_status ON outbox_events (status, created_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Consumer idempotency
-- ---------------------------------------------------------------------------------------------
-- At-least-once delivery means a consumer will eventually see the same event twice: a redelivery
-- after a crash, a rebalance, or a retry. Recording what has been handled is what makes the
-- second delivery harmless.

CREATE TABLE processed_events (
    event_id     UUID        NOT NULL,
    consumer     VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer)
);

COMMENT ON TABLE outbox_events IS 'Events written in the same transaction as the change they describe.';
COMMENT ON TABLE processed_events IS 'What each consumer has already handled, so a redelivery does nothing.';
