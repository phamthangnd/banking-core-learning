package com.example.bankcore.customer.web.dto;

import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.SortDirection;
import com.example.bankcore.customer.domain.CustomerSearchQuery;
import com.example.bankcore.customer.domain.CustomerSortField;
import com.example.bankcore.customer.domain.CustomerStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Query parameters of {@code GET /api/v1/customers}.
 *
 * <p>Bound from the query string by Spring ({@code ?page=0&size=20&sort=CREATED_AT&...}).
 * Unset parameters fall back to the defaults declared here, so a plain call still returns a
 * sensible first page.
 *
 * <p>{@code sort} and {@code direction} bind to enums, so an unknown value is rejected as a
 * 400 by the type-mismatch handler instead of reaching a query builder.
 *
 * @param page      zero-based page index
 * @param size      page size; the service caps it at {@code bankcore.customer.max-page-size}
 * @param sort      sort field from the allow-list
 * @param direction sort direction
 * @param name      case-insensitive fragment of the full name
 * @param email     exact email address
 * @param status    lifecycle state
 */
public record CustomerSearchRequest(
        @Min(value = 0, message = "must not be negative") Integer page,
        @Min(value = 1, message = "must be at least 1") Integer size,
        CustomerSortField sort,
        SortDirection direction,
        @Size(max = 150, message = "must be at most 150 characters") String name,
        @Size(max = 255, message = "must be at most 255 characters") String email,
        CustomerStatus status
) {

    public CustomerSearchQuery toQuery(int defaultPageSize) {
        PageRequest pageRequest = new PageRequest(
                page == null ? 0 : page,
                size == null ? defaultPageSize : size,
                (sort == null ? CustomerSortField.CREATED_AT : sort).property(),
                direction == null ? SortDirection.DESC : direction);

        return new CustomerSearchQuery(name, email, status, pageRequest);
    }
}
