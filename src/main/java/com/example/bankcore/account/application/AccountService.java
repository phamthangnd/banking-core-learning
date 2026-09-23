package com.example.bankcore.account.application;

import com.example.bankcore.account.config.AccountProperties;
import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountExceptions;
import com.example.bankcore.account.domain.AccountNumberGenerator;
import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.account.domain.AccountSearchQuery;
import com.example.bankcore.account.domain.AccountType;
import com.example.bankcore.common.money.Money;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerNotFoundException;
import com.example.bankcore.customer.domain.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Business rules for accounts.
 *
 * <p>Rules enforced here:
 * <ol>
 *   <li>an account may only be opened for an existing, non-closed customer;</li>
 *   <li>a customer may own many accounts, in different currencies and of different types;</li>
 *   <li>an account may only be activated once the owner's KYC is verified;</li>
 *   <li>an overdraft may only be granted on a type that allows one, up to the configured
 *       maximum;</li>
 *   <li>an account is closed, never deleted, and only with a zero balance.</li>
 * </ol>
 *
 * <p>Rule 3 is the reason this service reads the customer repository: the KYC decision lives in
 * the customer module, and the account aggregate cannot see it. Reading through the other
 * module's <em>port</em> keeps the dependency explicit and one-directional — the customer module
 * knows nothing about accounts.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final AccountProperties properties;
    private final java.time.Clock clock;

    public AccountService(AccountRepository accounts, CustomerRepository customers,
                          AccountProperties properties, java.time.Clock clock) {
        this.accounts = accounts;
        this.customers = customers;
        this.properties = properties;
        this.clock = clock;
    }

    /** Opens a {@code PENDING} account. Activation is a separate, deliberate step. */
    @Transactional
    @PreAuthorize("hasAuthority('account:write')")
    public Account open(AccountCommands.OpenAccount command) {
        Customer customer = customers.findById(command.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(command.customerId()));

        if (customer.isClosed()) {
            throw new AccountExceptions.AccountRuleViolationException(
                    "A closed customer cannot open an account");
        }

        String currency = command.currency() == null || command.currency().isBlank()
                ? properties.defaultCurrency()
                : command.currency();

        Money overdraftLimit = resolveOverdraftLimit(command.accountType(), command.overdraftLimit(), currency);

        String accountNumber = AccountNumberGenerator.generate(
                properties.numberPrefix(), accounts.nextAccountNumberSequence());

        Account account = Account.open(UUID.randomUUID(), accountNumber, customer.id(),
                command.accountType(), currency, overdraftLimit, clock.instant());

        Account saved = accounts.save(account);
        log.info("Account opened: id={} number={} customerId={} type={} currency={}",
                saved.id(), saved.accountNumber(), customer.id(), saved.accountType(), currency);
        return saved;
    }

    @PreAuthorize("hasAuthority('account:read')")
    public Account getById(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountExceptions.AccountNotFoundException(id));
    }

    @PreAuthorize("hasAuthority('account:read')")
    public Account getByAccountNumber(String accountNumber) {
        return accounts.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountExceptions.AccountNotFoundException(accountNumber));
    }

    @PreAuthorize("hasAuthority('account:read')")
    public PageResult<Account> search(AccountSearchQuery query) {
        return accounts.search(query);
    }

    /**
     * Activates an account.
     *
     * <p>The KYC check lives here rather than in {@link Account#activate}: it is a rule about two
     * aggregates, and the account cannot see the customer.
     */
    @Transactional
    @PreAuthorize("hasAuthority('account:write')")
    public Account activate(UUID id) {
        Account account = getById(id);
        Customer customer = customers.findById(account.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(account.customerId()));

        if (!customer.isKycVerified()) {
            throw new AccountExceptions.AccountRuleViolationException(
                    "An account cannot be activated before the customer's KYC is verified");
        }

        if (customer.isClosed()) {
            throw new AccountExceptions.AccountRuleViolationException(
                    "An account of a closed customer cannot be activated");
        }

        Account activated = accounts.save(account.activate(clock.instant()));
        log.info("Account activated: id={} number={}", activated.id(), activated.accountNumber());
        return activated;
    }

    @Transactional
    @PreAuthorize("hasAuthority('account:write')")
    public Account freeze(UUID id) {
        Account frozen = accounts.save(getById(id).freeze(clock.instant()));
        log.info("Account frozen: id={} number={}", frozen.id(), frozen.accountNumber());
        return frozen;
    }

    @Transactional
    @PreAuthorize("hasAuthority('account:write')")
    public Account unfreeze(UUID id) {
        Account active = accounts.save(getById(id).unfreeze(clock.instant()));
        log.info("Account unfrozen: id={} number={}", active.id(), active.accountNumber());
        return active;
    }

    @Transactional
    @PreAuthorize("hasAuthority('account:write')")
    public Account setOverdraftLimit(UUID id, AccountCommands.SetOverdraftLimit command) {
        Account account = getById(id);
        Money limit = resolveOverdraftLimit(account.accountType(), command.overdraftLimit(), account.currency());

        Account updated = accounts.save(account.withOverdraftLimit(limit, clock.instant()));
        log.info("Overdraft limit changed: id={} number={}", updated.id(), updated.accountNumber());
        return updated;
    }

    /** Closes an account. Idempotent: closing an already closed account changes nothing. */
    @Transactional
    @PreAuthorize("hasAuthority('account:close')")
    public Account close(UUID id) {
        Account account = getById(id);

        if (account.status().isTerminal()) {
            return account;
        }

        Account closed = accounts.save(account.close(clock.instant()));
        log.info("Account closed: id={} number={}", closed.id(), closed.accountNumber());
        return closed;
    }

    /**
     * Whether a customer still holds an account that is not closed.
     *
     * <p>Used by the customer module through its own port, so the dependency stays inverted:
     * the customer module declares what it needs and this module supplies it.
     */
    public boolean hasOpenAccounts(UUID customerId) {
        return accounts.existsOpenAccountForCustomer(customerId);
    }

    private Money resolveOverdraftLimit(AccountType type, BigDecimal requested, String currency) {
        BigDecimal amount = requested == null ? BigDecimal.ZERO : requested;

        if (amount.signum() < 0) {
            throw new AccountExceptions.AccountRuleViolationException("Overdraft limit must not be negative");
        }

        if (amount.signum() > 0 && !type.isOverdraftAllowed()) {
            throw new AccountExceptions.AccountRuleViolationException(
                    "Account type %s cannot have an overdraft".formatted(type));
        }

        BigDecimal maximum = type.maximumOverdraftLimit(properties.maximumOverdraftLimit());
        if (amount.compareTo(maximum) > 0) {
            throw new AccountExceptions.AccountRuleViolationException(
                    "Overdraft limit exceeds the maximum of %s".formatted(maximum.toPlainString()));
        }

        return new Money(amount, currency);
    }
}
