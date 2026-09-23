package com.example.bankcore.common.events.domain;

/** Where an outbox row is in its life. */
public enum OutboxStatus {

    /** Not published yet, or the last attempt failed and it will be retried. */
    PENDING,

    /** Handed to the broker successfully. */
    PUBLISHED,

    /** Gave up after the attempt limit; needs a human. */
    FAILED
}
