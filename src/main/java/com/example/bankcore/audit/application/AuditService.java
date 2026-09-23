package com.example.bankcore.audit.application;

import com.example.bankcore.audit.domain.AuditEvent;
import com.example.bankcore.audit.domain.AuditOutcome;
import com.example.bankcore.audit.domain.AuditRepository;
import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.common.trace.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes and reads the audit trail.
 *
 * <p>Entries are written with {@link Propagation#REQUIRES_NEW}, for the reason this codebase has
 * now met four times: an audited action that fails rolls its transaction back, and the record
 * that it was attempted has to survive that. A denied or failed action is precisely the one worth
 * auditing.
 *
 * <p>The actor is taken from the security context, never from a parameter. A caller that could
 * name the actor could name somebody else.
 */
@Service
@Transactional(readOnly = true)
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository repository;
    private final Clock clock;

    public AuditService(AuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String resourceType, String resourceId,
                       AuditOutcome outcome, String detail) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        UUID actorId = null;
        String actorName = null;
        if (authentication != null && authentication.isAuthenticated()) {
            actorName = authentication.getName();
            try {
                actorId = UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException notAUserId) {
                // Anonymous or a test principal; the name is still worth keeping.
                actorId = null;
            }
        }

        try {
            repository.append(new AuditEvent(UUID.randomUUID(), actorId, actorName, action,
                    resourceType, resourceId, outcome, CorrelationId.currentOrNull(),
                    detail, clock.instant()));
        } catch (RuntimeException ex) {
            // Auditing must never be the reason a legitimate operation fails. The failure is
            // logged loudly instead, because a silently missing audit trail is worse than a
            // noisy one.
            log.error("Failed to write an audit event: action={} resourceType={}", action, resourceType, ex);
        }
    }

    public void recordSuccess(String action, String resourceType, String resourceId) {
        record(action, resourceType, resourceId, AuditOutcome.SUCCESS, null);
    }

    public void recordFailure(String action, String resourceType, String resourceId, String detail) {
        record(action, resourceType, resourceId, AuditOutcome.FAILURE, detail);
    }

    @PreAuthorize("hasAuthority('audit:read')")
    public PageResult<AuditEvent> search(UUID actorId, String resourceType, String resourceId,
                                         Instant from, Instant to, PageRequest page) {
        return repository.search(actorId, resourceType, resourceId, from, to, page);
    }
}
