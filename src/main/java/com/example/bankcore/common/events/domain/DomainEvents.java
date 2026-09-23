package com.example.bankcore.common.events.domain;

/**
 * Topic and event-type names.
 *
 * <p>Constants rather than strings scattered across the code: a typo in a topic name produces a
 * new empty topic and silence, which is among the harder failures to notice.
 *
 * <p>Payloads carry identifiers and facts — never balances, amounts, credentials or personal
 * data. A consumer that needs the detail asks the API for it, with its own authorization
 * (CLAUDE.md section 4).
 */
public final class DomainEvents {

    public static final String TOPIC_TRANSACTIONS = "bankcore.transactions";
    public static final String TOPIC_AUDIT = "bankcore.audit";
    public static final String TOPIC_NOTIFICATIONS = "bankcore.notifications";

    public static final String TRANSACTION_POSTED = "TransactionPosted";
    public static final String TRANSACTION_REVERSED = "TransactionReversed";
    public static final String AUDIT_RECORDED = "AuditRecorded";
    public static final String NOTIFICATION_REQUESTED = "NotificationRequested";

    private DomainEvents() {
    }
}
