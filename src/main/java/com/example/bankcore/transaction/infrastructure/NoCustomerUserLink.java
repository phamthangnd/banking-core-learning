package com.example.bankcore.transaction.infrastructure;

import com.example.bankcore.transaction.application.AccountOwnerLookup;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * The current answer: there is no link between a customer and a login user.
 *
 * <p>Customers are records the bank holds; users are people who sign in. Nothing yet says which
 * user owns which customer, and inventing that mapping to make notifications appear would be
 * worse than not sending them — a notification delivered to the wrong person is a data breach.
 *
 * <p>Events are still published with the account id, so the moment the link exists a new
 * implementation of this port makes every past and future consumer work.
 */
@Component
public class NoCustomerUserLink implements AccountOwnerLookup {

    @Override
    public Optional<UUID> ownerUserIdOf(UUID accountId) {
        return Optional.empty();
    }
}
