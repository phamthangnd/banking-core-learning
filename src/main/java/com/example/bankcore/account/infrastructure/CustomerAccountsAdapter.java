package com.example.bankcore.account.infrastructure;

import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.customer.domain.CustomerAccountsPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Supplies the customer module with the one account fact it needs.
 *
 * <p>Deliberately goes to the repository rather than to {@code AccountService}: this is an
 * internal module-to-module question, not a user action, so it must not be subject to the
 * caller's account permissions. A teller closing a customer should not need {@code account:read}
 * for the system to check its own invariant.
 */
@Component
public class CustomerAccountsAdapter implements CustomerAccountsPort {

    private final AccountRepository accounts;

    public CustomerAccountsAdapter(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean hasOpenAccounts(UUID customerId) {
        return accounts.existsOpenAccountForCustomer(customerId);
    }
}
