package com.example.bankcore.common.pagination;

import java.util.Objects;

/**
 * A request for one page of results.
 *
 * <p>Deliberately not Spring Data's {@code Pageable}: paging is a domain concept, and the
 * repository port in the domain must not depend on a persistence framework
 * (CLAUDE.md section 2). The JPA adapter translates this into {@code Pageable} at the boundary.
 *
 * <p>Pagination is mandatory for collections that can grow (CLAUDE.md section 5). An endpoint
 * that returns "everything" works on a developer's laptop and takes the database down in
 * production.
 *
 * @param page          zero-based page index
 * @param size          number of elements per page, already capped by the caller
 * @param sortProperty  entity property to sort by; only values from a module's allow-list
 * @param direction     sort direction
 */
public record PageRequest(int page, int size, String sortProperty, SortDirection direction) {

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        Objects.requireNonNull(sortProperty, "sortProperty must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
    }

    public long offset() {
        return (long) page * size;
    }
}
