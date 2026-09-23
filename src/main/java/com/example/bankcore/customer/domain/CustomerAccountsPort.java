package com.example.bankcore.customer.domain;

import java.util.UUID;

/**
 * What the customer module needs to know about accounts, and nothing more.
 *
 * <p>Closing a customer that still holds an open account would leave money with no owner, so the
 * rule belongs to the customer lifecycle — but the fact it depends on lives in the account
 * module. Declaring the port <em>here</em> and implementing it <em>there</em> inverts the
 * dependency: the customer module stays unaware that accounts exist, and the account module
 * supplies the one answer it is asked for.
 *
 * <p>The alternative, calling {@code AccountService} from {@code CustomerService}, would make the
 * two modules mutually dependent and turn a modular monolith into a single tangled one
 * (CLAUDE.md section 2).
 */
public interface CustomerAccountsPort {

    /** Whether the customer holds at least one account that is not closed. */
    boolean hasOpenAccounts(UUID customerId);
}
