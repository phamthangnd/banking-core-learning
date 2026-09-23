package com.example.bankcore.audit.web;

import com.example.bankcore.audit.application.AuditService;
import com.example.bankcore.audit.domain.AuditEvent;
import com.example.bankcore.audit.domain.AuditOutcome;
import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.common.pagination.SortDirection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reading the audit trail. Requires {@code audit:read}, which only administrators hold. */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/events")
    public ApiResponse<List<AuditEventResponse>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        PageRequest request = new PageRequest(Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE), "occurredAt", SortDirection.DESC);

        PageResult<AuditEventResponse> result = auditService
                .search(actorId, resourceType, resourceId, from, to, request)
                .map(AuditEventResponse::from);

        return ApiResponse.success(result.content(), Map.of(
                "page", result.page(),
                "size", result.size(),
                "totalElements", result.totalElements(),
                "totalPages", result.totalPages(),
                "hasNext", result.hasNext()));
    }

    /** Response body for an audit event. */
    public record AuditEventResponse(UUID id, UUID actorId, String actorName, String action,
                                     String resourceType, String resourceId, AuditOutcome outcome,
                                     String traceId, String detail, Instant occurredAt) {

        static AuditEventResponse from(AuditEvent event) {
            return new AuditEventResponse(event.id(), event.actorId(), event.actorName(),
                    event.action(), event.resourceType(), event.resourceId(), event.outcome(),
                    event.traceId(), event.detail(), event.occurredAt());
        }
    }
}
