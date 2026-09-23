package com.example.bankcore.customer.infrastructure.persistence;

import com.example.bankcore.customer.domain.CustomerSearchQuery;
import com.example.bankcore.customer.domain.CustomerStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the WHERE clause of a customer search from the filters that are actually present.
 *
 * <p>Why not one JPQL query with {@code (:param is null or column = :param)} clauses:
 * <ul>
 *   <li><b>It does not work on PostgreSQL.</b> A null bind parameter inside a function has no
 *       inferable type, and the server rejects the statement with
 *       {@code function lower(bytea) does not exist}.</li>
 *   <li><b>It produces worse plans.</b> A dead {@code OR ... IS NULL} predicate still has to be
 *       evaluated and blocks the planner from using an index for that column.</li>
 * </ul>
 *
 * <p>A {@link Specification} is a small piece of the Criteria API: it builds the predicate tree
 * programmatically, so an absent filter contributes nothing at all to the generated SQL.
 */
final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    static Specification<CustomerEntity> from(CustomerSearchQuery query) {
        List<Specification<CustomerEntity>> predicates = new ArrayList<>();

        query.statusOrEmpty().ifPresent(status -> predicates.add(hasStatus(status)));
        query.emailOrEmpty().ifPresent(email -> predicates.add(hasEmail(email)));
        query.nameFragmentOrEmpty().ifPresent(fragment -> predicates.add(nameContains(fragment)));

        // No filters at all means "match everything", which Specification.allOf(empty) expresses.
        return Specification.allOf(predicates);
    }

    /** Uses ix_customers_status_created_at together with the default sort on created_at. */
    private static Specification<CustomerEntity> hasStatus(CustomerStatus status) {
        return (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    /** Exact match on the normalised address; served by the unique index ux_customers_email. */
    private static Specification<CustomerEntity> hasEmail(String normalizedEmail) {
        return (root, query, builder) -> builder.equal(root.get("email"), normalizedEmail);
    }

    /**
     * Case-insensitive "contains" on the full name.
     *
     * <p>The leading wildcard means no B-tree index can be used, including
     * {@code ix_customers_full_name_lower}: this predicate always scans. It is acceptable at the
     * current data size and is the first thing to revisit (trigram or full-text index) when the
     * table grows — Phase 08.
     */
    private static Specification<CustomerEntity> nameContains(String fragment) {
        String pattern = "%" + fragment.toLowerCase(Locale.ROOT) + "%";
        return (root, query, builder) -> builder.like(builder.lower(root.get("fullName")), pattern);
    }
}
