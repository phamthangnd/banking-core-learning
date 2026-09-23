package com.example.bankcore.audit.infrastructure.persistence;

import com.example.bankcore.audit.domain.AuditEvent;
import com.example.bankcore.audit.domain.AuditRepository;
import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.common.pagination.SortDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Adapter for the audit trail. */
@Repository
@Transactional(readOnly = true)
public class JpaAuditRepository implements AuditRepository {

    private final AuditEventJpaRepository events;

    public JpaAuditRepository(AuditEventJpaRepository events) {
        this.events = events;
    }

    @Override
    @Transactional
    public AuditEvent append(AuditEvent event) {
        return events.save(AuditEventEntity.fromDomain(event)).toDomain();
    }

    @Override
    public PageResult<AuditEvent> search(UUID actorId, String resourceType, String resourceId,
                                         Instant from, Instant to, PageRequest page) {
        List<Specification<AuditEventEntity>> predicates = new ArrayList<>();

        if (actorId != null) {
            predicates.add((root, query, builder) -> builder.equal(root.get("actorId"), actorId));
        }
        if (resourceType != null && !resourceType.isBlank()) {
            predicates.add((root, query, builder) -> builder.equal(root.get("resourceType"), resourceType));
        }
        if (resourceId != null && !resourceId.isBlank()) {
            predicates.add((root, query, builder) -> builder.equal(root.get("resourceId"), resourceId));
        }
        if (from != null) {
            predicates.add((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            predicates.add((root, query, builder) -> builder.lessThan(root.get("occurredAt"), to));
        }

        Sort.Direction direction = page.direction() == SortDirection.ASC
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page.page(), page.size(),
                Sort.by(direction, page.sortProperty()).and(Sort.by(Sort.Direction.ASC, "id")));

        Page<AuditEventEntity> result = events.findAll(Specification.allOf(predicates), pageable);

        return new PageResult<>(result.getContent().stream().map(AuditEventEntity::toDomain).toList(),
                page.page(), page.size(), result.getTotalElements());
    }
}
