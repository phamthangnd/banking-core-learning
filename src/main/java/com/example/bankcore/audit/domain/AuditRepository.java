package com.example.bankcore.audit.domain;

import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.PageResult;

import java.time.Instant;
import java.util.UUID;

/** Persistence port for the audit trail. Append and read; never update, never delete. */
public interface AuditRepository {

    AuditEvent append(AuditEvent event);

    PageResult<AuditEvent> search(UUID actorId, String resourceType, String resourceId,
                                  Instant from, Instant to, PageRequest page);
}
