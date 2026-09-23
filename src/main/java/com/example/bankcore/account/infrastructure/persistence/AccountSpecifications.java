package com.example.bankcore.account.infrastructure.persistence;

import com.example.bankcore.account.domain.AccountSearchQuery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the WHERE clause of an account search from the filters that are present.
 *
 * <p>Same reasoning as the customer module: a {@code (:param is null or column = :param)} query
 * does not work on PostgreSQL for parameters used inside functions, and a dead predicate keeps
 * the planner from using an index. The Criteria API contributes nothing for an absent filter.
 */
final class AccountSpecifications {

    private AccountSpecifications() {
    }

    static Specification<AccountEntity> from(AccountSearchQuery query) {
        List<Specification<AccountEntity>> predicates = new ArrayList<>();

        // customer_id and status are the leading columns of ix_accounts_customer_status.
        query.customerIdOrEmpty().ifPresent(customerId ->
                predicates.add((root, q, builder) -> builder.equal(root.get("customerId"), customerId)));
        query.statusOrEmpty().ifPresent(status ->
                predicates.add((root, q, builder) -> builder.equal(root.get("status"), status)));
        query.accountTypeOrEmpty().ifPresent(type ->
                predicates.add((root, q, builder) -> builder.equal(root.get("accountType"), type)));
        query.currencyOrEmpty().ifPresent(currency ->
                predicates.add((root, q, builder) -> builder.equal(root.get("currency"), currency)));

        return Specification.allOf(predicates);
    }
}
