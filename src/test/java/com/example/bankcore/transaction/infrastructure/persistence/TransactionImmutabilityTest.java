package com.example.bankcore.transaction.infrastructure.persistence;

import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.account.domain.AccountType;
import com.example.bankcore.common.money.Money;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerRepository;
import com.example.bankcore.support.DatabaseCleaner;
import com.example.bankcore.support.PostgresIntegrationTest;
import com.example.bankcore.transaction.domain.Transaction;
import com.example.bankcore.transaction.domain.TransactionRepository;
import com.example.bankcore.transaction.domain.TransactionStatus;
import com.example.bankcore.transaction.domain.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A posted financial record is immutable, and the database is what guarantees it.
 *
 * <p>Application code already refuses to rewrite a transaction, but the integrity of a ledger
 * should not depend on every future caller remembering that. These tests bypass the domain
 * entirely and issue raw SQL — the kind of thing that happens during an incident at 3am.
 */
@SpringBootTest
class TransactionImmutabilityTest extends PostgresIntegrationTest {

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private Clock clock;

    private Transaction posted;

    @BeforeEach
    void setUp() {
        databaseCleaner.clear();

        Customer owner = customers.save(Customer.register(UUID.randomUUID(), "Ledger Owner",
                "ledger@example.com", "+84901234567", LocalDate.of(1990, 1, 1), clock.instant()));

        Account account = accounts.save(Account.open(UUID.randomUUID(), "9004000000009", owner.id(),
                        AccountType.CHECKING, "VND", Money.zero("VND"), clock.instant())
                .activate(clock.instant()));

        posted = transactions.save(Transaction.posted(UUID.randomUUID(), "TXN-20260615-000000001",
                TransactionType.DEPOSIT, Money.of("100.0000", "VND"), null, account.id(),
                null, Money.of("100.0000", "VND"), "seed", clock.instant()));
    }

    @Test
    void shouldRefuseToChangeTheAmount() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update transactions set amount = 1 where id = ?", posted.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void shouldRefuseToRepointTheAccount() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update transactions set target_account_id = null where id = ?", posted.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void shouldRefuseToRewriteTheReferenceOrTheTime() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update transactions set reference = 'TXN-FAKE' where id = ?", posted.id()))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update transactions set occurred_at = now() where id = ?", posted.id()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void shouldRefuseToDeleteAFinancialRecord() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from transactions where id = ?", posted.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("must not be deleted");

        assertThat(transactions.findById(posted.id())).isPresent();
    }

    @Test
    void shouldAllowTheStatusToMoveForward() {
        // Reversal is how a posted transaction is corrected, so that one change must work.
        Transaction reversed = transactions.save(
                posted.withStatus(TransactionStatus.REVERSED, clock.instant()));

        assertThat(reversed.status()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(transactions.findById(posted.id()).orElseThrow().status())
                .isEqualTo(TransactionStatus.REVERSED);
    }

    @Test
    void shouldRefuseToChangeATerminalStatus() {
        jdbcTemplate.update("update transactions set status = 'REVERSED' where id = ?", posted.id());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update transactions set status = 'POSTED' where id = ?", posted.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("terminal state");
    }

    @Test
    void shouldRefuseAMalformedTransactionShape() {
        // A deposit with a source account is not a deposit. The check constraint says so even if
        // the domain model is bypassed.
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into transactions (id, reference, transaction_type, status, currency, amount,
                                          source_account_id, target_account_id, occurred_at, posted_at, created_at)
                values (?, 'TXN-BAD', 'DEPOSIT', 'POSTED', 'VND', 1, ?, ?, now(), now(), now())
                """, UUID.randomUUID(), posted.targetAccountId(), posted.targetAccountId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void shouldRefuseANonPositiveAmount() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into transactions (id, reference, transaction_type, status, currency, amount,
                                          target_account_id, occurred_at, posted_at, created_at)
                values (?, 'TXN-ZERO', 'DEPOSIT', 'POSTED', 'VND', 0, ?, now(), now(), now())
                """, UUID.randomUUID(), posted.targetAccountId()))
                .isInstanceOf(DataAccessException.class);
    }
}
