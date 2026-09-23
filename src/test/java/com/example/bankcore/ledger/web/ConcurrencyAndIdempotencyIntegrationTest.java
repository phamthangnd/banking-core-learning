package com.example.bankcore.ledger.web;

import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.account.domain.AccountType;
import com.example.bankcore.common.money.Money;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerRepository;
import com.example.bankcore.ledger.application.LedgerService;
import com.example.bankcore.support.DatabaseCleaner;
import com.example.bankcore.support.PostgresIntegrationTest;
import com.example.bankcore.transaction.application.TransactionCommands;
import com.example.bankcore.transaction.application.TransactionService;
import com.example.bankcore.transaction.domain.TransactionExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The three invariants of Phase 06, tested the only way they can be: by running the real thing.
 *
 * <ol>
 *   <li>concurrent updates cannot corrupt balances;</li>
 *   <li>one idempotency key cannot create more than one financial effect;</li>
 *   <li>total debits equal total credits, and the ledger reconciles with the accounts.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "66666666-6666-6666-6666-666666666666",
        authorities = {"transaction:read", "transaction:write", "account:read"})
class ConcurrencyAndIdempotencyIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private Clock clock;

    private Account first;
    private Account second;

    /**
     * The authentication put in place by {@code @WithMockUser}, captured before anything clears
     * it. Spring Security's filter chain empties the SecurityContext when a MockMvc request
     * ends, and worker threads need a context of their own in any case — it is thread-local.
     */
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        authentication = SecurityContextHolder.getContext().getAuthentication();
        databaseCleaner.clear();

        Customer owner = customers.save(Customer.register(UUID.randomUUID(), "Concurrency Owner",
                "concurrency@example.com", "+84901234567", LocalDate.of(1990, 1, 1), clock.instant()));

        first = activeAccount(owner.id(), "9004000000101");
        second = activeAccount(owner.id(), "9004000000102");
    }

    private Account activeAccount(UUID ownerId, String number) {
        return accounts.save(Account.open(UUID.randomUUID(), number, ownerId, AccountType.CHECKING,
                "VND", Money.zero("VND"), clock.instant()).activate(clock.instant()));
    }

    private BigDecimal balanceOf(Account account) {
        return accounts.findById(account.id()).orElseThrow().balance().amount();
    }

    /**
     * Seeds an account through the API rather than the service.
     *
     * <p>Spring Security's filter chain clears the SecurityContext when a MockMvc request ends,
     * so a direct service call after one would find no authentication. Going through the API
     * keeps every call in this class on the same footing.
     */
    private void deposit(Account account, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": "%s", "amount": %s, "currency": "VND", "description": "seed"}
                                """.formatted(account.id(), amount)))
                .andExpect(status().isCreated());

        restoreAuthentication();
    }

    /** Puts the captured authentication back after a MockMvc request has cleared it. */
    private void restoreAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Runs {@code task} on {@code threads} threads released simultaneously.
     *
     * <p>The security context is thread-local, so each worker has to be given one; without the
     * latch the threads would run one after another and no race would ever happen.
     */
    private int runConcurrently(int threads, Callable<Boolean> task) throws Exception {
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var successes = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    try {
                        start.await();
                        if (Boolean.TRUE.equals(task.call())) {
                            successes.incrementAndGet();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (Exception expected) {
                        // A losing attempt is a legitimate outcome; corrupting a balance is not.
                    } finally {
                        SecurityContextHolder.clearContext();
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        }

        restoreAuthentication();
        return successes.get();
    }

    @Nested
    class Concurrency {

        @Test
        void concurrentDepositsShouldNotLoseAnyMoney() throws Exception {
            int threads = 16;

            runConcurrently(threads, () -> {
                transactionService.deposit(new TransactionCommands.Deposit(
                        first.id(), new BigDecimal("10.00"), "VND", "concurrent"));
                return true;
            });

            // Read-modify-write on a balance loses updates unless the rows are locked.
            assertThat(balanceOf(first)).isEqualByComparingTo("160.00");
        }

        @Test
        void concurrentWithdrawalsShouldNeverOverdrawTheAccount() throws Exception {
            deposit(first, "100.00");

            // Ten withdrawals of 10 fit exactly; the rest must fail.
            int successes = runConcurrently(20, () -> {
                transactionService.withdraw(new TransactionCommands.Withdraw(
                        first.id(), new BigDecimal("10.00"), "VND", "concurrent"));
                return true;
            });

            assertThat(successes).isEqualTo(10);
            assertThat(balanceOf(first)).isEqualByComparingTo("0.00");
        }

        @Test
        void concurrentTransfersInBothDirectionsShouldNotDeadlockOrCreateMoney() throws Exception {
            deposit(first, "500.00");
            deposit(second, "500.00");

            // Opposite directions at the same time: without a fixed lock order these deadlock.
            int successes = runConcurrently(20, () -> {
                boolean forward = Thread.currentThread().threadId() % 2 == 0;
                Account from = forward ? first : second;
                Account to = forward ? second : first;

                transactionService.transfer(new TransactionCommands.Transfer(
                        from.id(), to.id(), new BigDecimal("10.00"), "VND", "concurrent"));
                return true;
            });

            assertThat(successes).isEqualTo(20);
            // Money moved between the two, but none was created or destroyed.
            assertThat(balanceOf(first).add(balanceOf(second))).isEqualByComparingTo("1000.00");
        }

        @Test
        void concurrentWorkShouldLeaveTheLedgerBalanced() throws Exception {
            deposit(first, "200.00");

            runConcurrently(12, () -> {
                transactionService.transfer(new TransactionCommands.Transfer(
                        first.id(), second.id(), new BigDecimal("10.00"), "VND", "concurrent"));
                return true;
            });

            var reconciliation = ledgerService.reconcile();
            assertThat(reconciliation.balanced()).isTrue();

            assertThat(ledgerService.reconcileAccount(first.id()).reconciled()).isTrue();
            assertThat(ledgerService.reconcileAccount(second.id()).reconciled()).isTrue();
        }
    }

    @Nested
    class Idempotency {

        private String transferBody(String amount) {
            return """
                    {"sourceAccountId": "%s", "targetAccountId": "%s", "amount": %s, "currency": "VND"}
                    """.formatted(first.id(), second.id(), amount);
        }

        @Test
        void aRepeatedRequestShouldMoveTheMoneyOnlyOnce() throws Exception {
            deposit(first, "500.00");
            String key = UUID.randomUUID().toString();

            String firstResponse = mockMvc.perform(post("/api/v1/transactions/transfer")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(transferBody("100")))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String secondResponse = mockMvc.perform(post("/api/v1/transactions/transfer")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(transferBody("100")))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            // The retry is answered with the original transaction, not a new one.
            assertThat(objectMapper.readTree(secondResponse).path("data").path("id").asText())
                    .isEqualTo(objectMapper.readTree(firstResponse).path("data").path("id").asText());

            assertThat(balanceOf(first)).isEqualByComparingTo("400.00");
            assertThat(balanceOf(second)).isEqualByComparingTo("100.00");
        }

        @Test
        void theSameKeyWithADifferentRequestShouldBeRefused() throws Exception {
            deposit(first, "500.00");
            String key = UUID.randomUUID().toString();

            mockMvc.perform(post("/api/v1/transactions/transfer")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(transferBody("100")))
                    .andExpect(status().isCreated());

            // Answering this with the first transfer's result would tell the caller that its
            // 250 had been sent.
            mockMvc.perform(post("/api/v1/transactions/transfer")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(transferBody("250")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_CONFLICT"));

            assertThat(balanceOf(first)).isEqualByComparingTo("400.00");
        }

        @Test
        void concurrentRetriesShouldProduceExactlyOneEffect() throws Exception {
            deposit(first, "500.00");
            String key = UUID.randomUUID().toString();

            // The real scenario: a client times out and retries while the first attempt is still
            // in flight. Only one of them may move money.
            runConcurrently(8, () -> {
                mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(transferBody("100")));
                return true;
            });

            assertThat(balanceOf(first)).isEqualByComparingTo("400.00");
            assertThat(balanceOf(second)).isEqualByComparingTo("100.00");
        }

        @Test
        void aFailedRequestShouldReleaseItsKeyForARetry() throws Exception {
            String key = UUID.randomUUID().toString();

            // Nothing deposited yet, so this is refused.
            mockMvc.perform(post("/api/v1/transactions/transfer")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(transferBody("100")))
                    .andExpect(status().isUnprocessableEntity());

            deposit(first, "500.00");

            // Same key, same request: now that the cause is fixed, the retry must be allowed.
            mockMvc.perform(post("/api/v1/transactions/transfer")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(transferBody("100")))
                    .andExpect(status().isCreated());

            assertThat(balanceOf(second)).isEqualByComparingTo("100.00");
        }

        @Test
        void withoutAKeyEachRequestShouldStillBeItsOwnMovement() throws Exception {
            deposit(first, "500.00");

            mockMvc.perform(post("/api/v1/transactions/transfer")
                    .contentType(MediaType.APPLICATION_JSON).content(transferBody("100")))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/v1/transactions/transfer")
                    .contentType(MediaType.APPLICATION_JSON).content(transferBody("100")))
                    .andExpect(status().isCreated());

            assertThat(balanceOf(second)).isEqualByComparingTo("200.00");
        }
    }

    @Nested
    class Ledger {

        @Test
        void everyMovementShouldWriteABalancedPair() throws Exception {
            deposit(first, "100.00");

            String response = mockMvc.perform(get("/api/v1/ledger/reconciliation"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            var report = objectMapper.readTree(response).path("data");
            assertThat(report.path("balanced").asBoolean()).isTrue();
            assertThat(report.path("debits").path("VND").decimalValue()).isEqualByComparingTo("100.0000");
            assertThat(report.path("credits").path("VND").decimalValue()).isEqualByComparingTo("100.0000");
        }

        @Test
        void aDepositShouldHaveABankLegAndACustomerLeg() throws Exception {
            var transaction = transactionService.deposit(new TransactionCommands.Deposit(
                    first.id(), new BigDecimal("100.00"), "VND", "seed"));

            var entries = ledgerService.entriesOfTransaction(transaction.id());

            assertThat(entries).hasSize(2);
            assertThat(entries).anyMatch(entry -> entry.systemAccount() != null && entry.isDebit());
            assertThat(entries).anyMatch(entry -> first.id().equals(entry.accountId()) && !entry.isDebit());
        }

        @Test
        void aFailedMovementShouldWriteNoLedgerEntries() {
            // Failed transactions leave no partial state: a record that it was attempted, and
            // nothing in the ledger.
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                            transactionService.withdraw(new TransactionCommands.Withdraw(
                                    first.id(), new BigDecimal("50.00"), "VND", "too much")))
                    .isInstanceOf(TransactionExceptions.TransactionRejectedException.class);

            assertThat(ledgerService.entriesOfAccount(first.id())).isEmpty();
            assertThat(ledgerService.reconcile().balanced()).isTrue();
            assertThat(balanceOf(first)).isEqualByComparingTo("0");
        }

        @Test
        void aReversalShouldLeaveTheLedgerBalancedAndTheMoneyBack() throws Exception {
            deposit(first, "300.00");
            var transfer = transactionService.transfer(new TransactionCommands.Transfer(
                    first.id(), second.id(), new BigDecimal("120.00"), "VND", "oops"));

            var compensating = transactionService.reverse(transfer.id(), "wrong account");

            assertThat(compensating.sourceAccountId()).isEqualTo(second.id());
            assertThat(compensating.targetAccountId()).isEqualTo(first.id());
            assertThat(balanceOf(first)).isEqualByComparingTo("300.00");
            assertThat(balanceOf(second)).isEqualByComparingTo("0.00");

            // The original is kept and marked, never edited away.
            assertThat(transactionService.getById(transfer.id()).status().name()).isEqualTo("REVERSED");
            assertThat(ledgerService.reconcile().balanced()).isTrue();
            assertThat(ledgerService.entriesOfTransaction(compensating.id())).hasSize(2);
        }

        @Test
        void accountBalancesShouldReconcileWithTheLedger() throws Exception {
            deposit(first, "250.00");
            transactionService.transfer(new TransactionCommands.Transfer(
                    first.id(), second.id(), new BigDecimal("75.00"), "VND", "split"));
            transactionService.withdraw(new TransactionCommands.Withdraw(
                    first.id(), new BigDecimal("25.00"), "VND", "cash out"));

            for (Account account : List.of(first, second)) {
                var report = ledgerService.reconcileAccount(account.id());
                assertThat(report.reconciled())
                        .describedAs("stored %s vs derived %s",
                                report.storedBalance().amount(), report.derivedBalance().amount())
                        .isTrue();
            }
        }
    }
}
