package com.example.bankcore.audit.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One entry in the audit trail: who did what, to which resource, when, and how it ended.
 *
 * <p>Append-only, enforced by database triggers. An audit trail that can be edited proves
 * nothing, and the first thing an intruder edits is the record of the intrusion.
 *
 * <p>What deliberately never appears in {@code detail}: passwords, tokens, balances, full account
 * numbers, or anything else a reader of the trail should not be entitled to (CLAUDE.md
 * section 4). The entry says <em>that</em> something happened and to which record, not what the
 * record contains.
 *
 * @param id           identity
 * @param actorId      user who acted, or {@code null} for an unauthenticated action
 * @param actorName    username at the time, kept so the trail survives a rename
 * @param action       what was attempted, for example {@code CUSTOMER_CLOSED}
 * @param resourceType kind of record acted on
 * @param resourceId   which record
 * @param outcome      success, failure or denied
 * @param traceId      correlation id, so the entry leads to its log lines
 * @param detail       short, non-sensitive context
 * @param occurredAt   when it happened (UTC)
 */
public record AuditEvent(
        UUID id,
        UUID actorId,
        String actorName,
        String action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        String traceId,
        String detail,
        Instant occurredAt
) {

    private static final int MAX_DETAIL = 500;

    public AuditEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");

        if (detail != null && detail.length() > MAX_DETAIL) {
            detail = detail.substring(0, MAX_DETAIL);
        }
    }
}
