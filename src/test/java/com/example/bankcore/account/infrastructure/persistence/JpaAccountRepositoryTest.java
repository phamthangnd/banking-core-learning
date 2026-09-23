package com.example.bankcore.account.infrastructure.persistence;

import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.account.domain.AccountSearchQuery;
import com.example.bankcore.account.domain.AccountStatus;
import com.example.bankcore.account.domain.AccountType;
import com.example.bankcore.common.money.Money;
import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.SortDirection;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerRepository;
import com.example.bankcore.customer.infrastructure.persistence.CustomerJpaRepository;
import com.example.bankcore.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The account adapter against a real PostgreSQL, including the check constraints that make the
 * banking invariants true at the storage layer and not only in Java.
 */
@SpringBootTest
class JpaAccountRepositoryTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private CustomerJpaRepository customerJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.example.bankcore.support.DatabaseCleaner databaseCleaner;

    private Customer owner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clear();

        owner = customers.save(Customer.register(UUID.randomUUID(), "Account Owner",
                "owner@example.com", "+84901234567", LocalDate.of(1990, 1, 1), NOW));
    }

    private Account open(AccountType type, String currency, String overdraft) {
        return accounts.save(Account.open(UUID.randomUUID(),
                "9004" + "%09d".formatted(accounts.nextAccountNumberSequence()) + "0",
                owner.id(), type, currency, Money.of(overdraft, currency), NOW));
    }

    @Test
    void shouldRoundTripAnAccountThroughPostgres() {
        Account saved = open(AccountType.CHECKING, "VND", "1000");

        Account loaded = accounts.findById(saved.id()).orElseThrow();

        assertThat(loaded.accountNumber()).isEqualTo(saved.accountNumber());
        assertThat(loaded.customerId()).isEqualTo(owner.id());
        assertThat(loaded.accountType()).isEqualTo(AccountType.CHECKING);
        assertThat(loaded.currency()).isEqualTo("VND");
        assertThat(loaded.balance().amount()).isEqualByComparingTo("0");
        assertThat(loaded.overdraftLimit().amount()).isEqualByComparingTo("1000");
        assertThat(loaded.status()).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void shouldKeepAmountsExactAcrossTheDatabase() {
        Account account = accounts.save(open(AccountType.CHECKING, "VND", "0")
                .activate(NOW)
                .credit(Money.of("0.1234", "VND"), NOW));

        // NUMERIC(19,4) holds the value exactly; a floating-point column would not.
        assertThat(accounts.findById(account.id()).orElseThrow().balance().amount())
                .isEqualByComparingTo("0.1234");
    }

    @Test
    void shouldFindByAccountNumber() {
        Account saved = open(AccountType.SAVINGS, "VND", "0");

        assertThat(accounts.findByAccountNumber(saved.accountNumber())).isPresent();
        assertThat(accounts.findByAccountNumber("0000000000000")).isEmpty();
    }

    @Test
    void shouldRejectADuplicateAccountNumber() {
        Account saved = open(AccountType.SAVINGS, "VND", "0");

        assertThatThrownBy(() -> accounts.save(Account.open(UUID.randomUUID(), saved.accountNumber(),
                owner.id(), AccountType.SAVINGS, "VND", Money.zero("VND"), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldLetTheDatabaseRejectABalanceBeyondTheOverdraft() {
        Account account = open(AccountType.CHECKING, "VND", "100");

        // Bypassing the domain entirely: the constraint is the last line of defence, and it has
        // to hold even against a bug or a manual UPDATE.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update accounts set balance = -100.0001 where id = ?", account.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldLetTheDatabaseRejectClosingAnAccountWithMoneyOnIt() {
        Account account = open(AccountType.CHECKING, "VND", "0");
        jdbcTemplate.update("update accounts set balance = 100 where id = ?", account.id());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update accounts set status = 'CLOSED', closed_at = now() where id = ?", account.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldLetTheDatabaseRejectAnUnknownStatus() {
        Account account = open(AccountType.CHECKING, "VND", "0");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update accounts set status = 'DELETED' where id = ?", account.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectAnAccountForAnUnknownCustomer() {
        assertThatThrownBy(() -> accounts.save(Account.open(UUID.randomUUID(), "90040000000001",
                UUID.randomUUID(), AccountType.SAVINGS, "VND", Money.zero("VND"), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldHandOutStrictlyIncreasingSequenceValues() {
        long first = accounts.nextAccountNumberSequence();
        long second = accounts.nextAccountNumberSequence();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    void shouldFilterAndPageAccounts() {
        open(AccountType.CHECKING, "VND", "0");
        open(AccountType.SAVINGS, "VND", "0");
        open(AccountType.SAVINGS, "USD", "0");

        var savings = accounts.search(new AccountSearchQuery(owner.id(), null, AccountType.SAVINGS, null,
                new PageRequest(0, 10, "openedAt", SortDirection.ASC)));
        var usd = accounts.search(new AccountSearchQuery(null, null, null, "USD",
                new PageRequest(0, 10, "openedAt", SortDirection.ASC)));
        var firstPage = accounts.search(new AccountSearchQuery(owner.id(), null, null, null,
                new PageRequest(0, 2, "openedAt", SortDirection.ASC)));

        assertThat(savings.totalElements()).isEqualTo(2);
        assertThat(usd.totalElements()).isEqualTo(1);
        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.hasNext()).isTrue();
    }

    @Test
    void shouldReportWhetherACustomerHoldsOpenAccounts() {
        Account account = open(AccountType.CHECKING, "VND", "0");

        assertThat(accounts.existsOpenAccountForCustomer(owner.id())).isTrue();

        accounts.save(account.activate(NOW).close(NOW));

        assertThat(accounts.existsOpenAccountForCustomer(owner.id())).isFalse();
    }

    @Test
    void shouldIncrementTheVersionOnUpdate() {
        Account account = open(AccountType.CHECKING, "VND", "0");
        long initial = accountJpaRepository.findById(account.id()).orElseThrow().getVersion();

        accounts.save(account.activate(NOW));

        assertThat(accountJpaRepository.findById(account.id()).orElseThrow().getVersion())
                .isGreaterThan(initial);
    }

    @Test
    void shouldStoreTheBalanceWithFourDecimals() {
        Account account = open(AccountType.CHECKING, "VND", "0");

        BigDecimal stored = jdbcTemplate.queryForObject(
                "select balance from accounts where id = ?", BigDecimal.class, account.id());

        assertThat(stored.scale()).isEqualTo(4);
    }
}
