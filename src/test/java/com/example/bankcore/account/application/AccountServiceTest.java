package com.example.bankcore.account.application;

import com.example.bankcore.account.config.AccountProperties;
import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountExceptions;
import com.example.bankcore.account.domain.AccountNumberGenerator;
import com.example.bankcore.account.domain.AccountSearchQuery;
import com.example.bankcore.account.domain.AccountStatus;
import com.example.bankcore.account.domain.AccountType;
import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.SortDirection;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerNotFoundException;
import com.example.bankcore.customer.domain.CustomerRepository;
import com.example.bankcore.customer.domain.CustomerSearchQuery;
import com.example.bankcore.customer.domain.KycStatus;
import com.example.bankcore.common.pagination.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Account business rules. Authorization is not exercised here — {@code @PreAuthorize} is a proxy
 * concern and the service is constructed directly; the permission checks are covered end to end
 * by {@code AccountAuthorizationIntegrationTest}.
 */
class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");

    private final FakeAccountRepository accounts = new FakeAccountRepository();
    private final FakeCustomerRepository customers = new FakeCustomerRepository();

    private AccountService service;
    private Customer verifiedCustomer;
    private Customer pendingCustomer;

    @BeforeEach
    void setUp() {
        service = new AccountService(accounts, customers,
                new AccountProperties("9004", "VND", new BigDecimal("1000000")),
                Clock.fixed(NOW, ZoneOffset.UTC));

        pendingCustomer = customers.save(Customer.register(UUID.randomUUID(), "Pending Person",
                "pending@example.com", "+84901234567", LocalDate.of(1990, 1, 1), NOW));
        verifiedCustomer = customers.save(Customer.register(UUID.randomUUID(), "Verified Person",
                "verified@example.com", "+84909999999", LocalDate.of(1990, 1, 1), NOW)
                .withKycStatus(KycStatus.VERIFIED, NOW));
    }

    private Account open(Customer customer, AccountType type) {
        return service.open(new AccountCommands.OpenAccount(customer.id(), type, "VND", null));
    }

    @Nested
    class Opening {

        @Test
        void shouldOpenAPendingAccountWithAValidNumber() {
            Account account = open(verifiedCustomer, AccountType.CHECKING);

            assertThat(account.status()).isEqualTo(AccountStatus.PENDING);
            assertThat(account.balance().isZero()).isTrue();
            assertThat(account.accountNumber()).startsWith("9004");
            assertThat(AccountNumberGenerator.isValid(account.accountNumber())).isTrue();
        }

        @Test
        void shouldLetOneCustomerOwnManyAccounts() {
            Account checking = open(verifiedCustomer, AccountType.CHECKING);
            Account savings = open(verifiedCustomer, AccountType.SAVINGS);
            Account foreign = service.open(new AccountCommands.OpenAccount(
                    verifiedCustomer.id(), AccountType.SAVINGS, "USD", null));

            assertThat(checking.accountNumber()).isNotEqualTo(savings.accountNumber());

            var page = service.search(AccountSearchQuery.forCustomer(verifiedCustomer.id(),
                    new PageRequest(0, 10, "openedAt", SortDirection.ASC)));

            assertThat(page.totalElements()).isEqualTo(3);
            assertThat(page.content()).extracting(Account::currency).contains("VND", "USD");
            assertThat(foreign.currency()).isEqualTo("USD");
        }

        @Test
        void shouldOpenAnAccountEvenBeforeKycIsVerified() {
            // Opening is allowed; it is activation that requires verification.
            assertThat(open(pendingCustomer, AccountType.CHECKING).status())
                    .isEqualTo(AccountStatus.PENDING);
        }

        @Test
        void shouldUseTheDefaultCurrencyWhenNoneIsGiven() {
            Account account = service.open(new AccountCommands.OpenAccount(
                    verifiedCustomer.id(), AccountType.SAVINGS, null, null));

            assertThat(account.currency()).isEqualTo("VND");
        }

        @Test
        void shouldRefuseAnUnknownCustomer() {
            assertThatThrownBy(() -> service.open(new AccountCommands.OpenAccount(
                    UUID.randomUUID(), AccountType.CHECKING, "VND", null)))
                    .isInstanceOf(CustomerNotFoundException.class);
        }

        @Test
        void shouldRefuseAClosedCustomer() {
            Customer closed = customers.save(verifiedCustomer.close(NOW));

            assertThatThrownBy(() -> open(closed, AccountType.CHECKING))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class)
                    .hasMessageContaining("closed customer");
        }

        @Test
        void shouldGrantAnOverdraftWithinTheConfiguredMaximum() {
            Account account = service.open(new AccountCommands.OpenAccount(
                    verifiedCustomer.id(), AccountType.CHECKING, "VND", new BigDecimal("500000")));

            assertThat(account.overdraftLimit().amount()).isEqualByComparingTo("500000");
        }

        @Test
        void shouldRefuseAnOverdraftAboveTheConfiguredMaximum() {
            assertThatThrownBy(() -> service.open(new AccountCommands.OpenAccount(
                    verifiedCustomer.id(), AccountType.CHECKING, "VND", new BigDecimal("2000000"))))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class)
                    .hasMessageContaining("maximum");
        }

        @Test
        void shouldRefuseAnOverdraftOnASavingsAccount() {
            assertThatThrownBy(() -> service.open(new AccountCommands.OpenAccount(
                    verifiedCustomer.id(), AccountType.SAVINGS, "VND", new BigDecimal("1"))))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class);
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void shouldActivateAnAccountOfAVerifiedCustomer() {
            Account account = open(verifiedCustomer, AccountType.CHECKING);

            Account activated = service.activate(account.id());

            assertThat(activated.status()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(activated.canTransact()).isTrue();
        }

        @Test
        void shouldRefuseActivationWhileKycIsPending() {
            Account account = open(pendingCustomer, AccountType.CHECKING);

            assertThatThrownBy(() -> service.activate(account.id()))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class)
                    .hasMessageContaining("KYC");
        }

        @Test
        void shouldAllowActivationOnceKycIsVerified() {
            Account account = open(pendingCustomer, AccountType.CHECKING);
            customers.save(pendingCustomer.withKycStatus(KycStatus.VERIFIED, NOW));

            assertThat(service.activate(account.id()).status()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void shouldFreezeAndUnfreeze() {
            Account account = service.activate(open(verifiedCustomer, AccountType.CHECKING).id());

            assertThat(service.freeze(account.id()).canTransact()).isFalse();
            assertThat(service.unfreeze(account.id()).canTransact()).isTrue();
        }

        @Test
        void shouldCloseAndBeIdempotent() {
            Account account = service.activate(open(verifiedCustomer, AccountType.CHECKING).id());

            Account closed = service.close(account.id());
            Account closedAgain = service.close(account.id());

            assertThat(closed.status()).isEqualTo(AccountStatus.CLOSED);
            assertThat(closedAgain.closedAt()).isEqualTo(closed.closedAt());
        }

        @Test
        void shouldRefuseToCloseAnAccountHoldingMoney() {
            Account account = service.activate(open(verifiedCustomer, AccountType.CHECKING).id());
            accounts.save(account.credit(com.example.bankcore.common.money.Money.of("100", "VND"), NOW));

            assertThatThrownBy(() -> service.close(account.id()))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class)
                    .hasMessageContaining("zero balance");
        }

        @Test
        void shouldFailForAnUnknownAccount() {
            assertThatThrownBy(() -> service.getById(UUID.randomUUID()))
                    .isInstanceOf(AccountExceptions.AccountNotFoundException.class);
        }
    }

    @Nested
    class Search {

        @Test
        void shouldFilterByStatusAndType() {
            Account checking = open(verifiedCustomer, AccountType.CHECKING);
            open(verifiedCustomer, AccountType.SAVINGS);
            service.activate(checking.id());

            var active = service.search(new AccountSearchQuery(null, AccountStatus.ACTIVE, null, null,
                    new PageRequest(0, 10, "openedAt", SortDirection.ASC)));
            var savings = service.search(new AccountSearchQuery(null, null, AccountType.SAVINGS, null,
                    new PageRequest(0, 10, "openedAt", SortDirection.ASC)));

            assertThat(active.content()).extracting(Account::id).containsExactly(checking.id());
            assertThat(savings.content()).hasSize(1);
        }

        @Test
        void shouldReportOpenAccountsForACustomer() {
            Account account = open(verifiedCustomer, AccountType.CHECKING);

            assertThat(service.hasOpenAccounts(verifiedCustomer.id())).isTrue();

            service.close(account.id());

            assertThat(service.hasOpenAccounts(verifiedCustomer.id())).isFalse();
        }
    }

    /** Minimal customer store; the account service only reads from it. */
    private static final class FakeCustomerRepository implements CustomerRepository {

        private final Map<UUID, Customer> customers = new LinkedHashMap<>();

        @Override
        public Customer save(Customer customer) {
            customers.put(customer.id(), customer);
            return customer;
        }

        @Override
        public Optional<Customer> findById(UUID id) {
            return Optional.ofNullable(customers.get(id));
        }

        @Override
        public Optional<Customer> findByEmail(String normalizedEmail) {
            return customers.values().stream()
                    .filter(customer -> customer.email().equals(normalizedEmail))
                    .findFirst();
        }

        @Override
        public PageResult<Customer> search(CustomerSearchQuery query) {
            return PageResult.empty(query.page());
        }
    }
}
