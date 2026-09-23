package com.example.bankcore.common.events;

import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.account.domain.AccountType;
import com.example.bankcore.common.events.domain.DomainEvents;
import com.example.bankcore.common.events.domain.OutboxEvent;
import com.example.bankcore.common.events.domain.OutboxRepository;
import com.example.bankcore.common.events.domain.OutboxStatus;
import com.example.bankcore.common.events.infrastructure.IdempotentConsumer;
import com.example.bankcore.common.money.Money;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerRepository;
import com.example.bankcore.support.DatabaseCleaner;
import com.example.bankcore.support.PostgresIntegrationTest;
import com.example.bankcore.transaction.application.TransactionCommands;
import com.example.bankcore.transaction.application.TransactionService;
import com.example.bankcore.transaction.domain.TransactionExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The transactional outbox and consumer deduplication, without a broker.
 *
 * <p>Kafka itself is not what these tests are about: the properties that matter are that an event
 * commits with the change it describes, disappears with it when it rolls back, and is handled at
 * most once however often it is delivered.
 */
@SpringBootTest(properties = "bankcore.events.enabled=false")
@WithMockUser(username = "88888888-8888-8888-8888-888888888888",
        authorities = {"transaction:read", "transaction:write", "account:read"})
class OutboxIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private TransactionService transactions;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private IdempotentConsumer idempotentConsumer;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private Clock clock;

    private Account account;

    @BeforeEach
    void setUp() {
        databaseCleaner.clear();

        Customer owner = customers.save(Customer.register(UUID.randomUUID(), "Event Owner",
                "events@example.com", "+84901234567", LocalDate.of(1990, 1, 1), clock.instant()));

        account = accounts.save(Account.open(UUID.randomUUID(), "9004000000301", owner.id(),
                AccountType.CHECKING, "VND", Money.zero("VND"), clock.instant()).activate(clock.instant()));
    }

    private List<OutboxEvent> pending() {
        return outbox.findPending(100);
    }

    @Test
    void aPostedTransactionShouldQueueAnEvent() throws Exception {
        var posted = transactions.deposit(new TransactionCommands.Deposit(
                account.id(), new BigDecimal("100.00"), "VND", "seed"));

        List<OutboxEvent> events = pending();

        assertThat(events).hasSize(1);
        OutboxEvent event = events.get(0);
        assertThat(event.topic()).isEqualTo(DomainEvents.TOPIC_TRANSACTIONS);
        assertThat(event.eventType()).isEqualTo(DomainEvents.TRANSACTION_POSTED);
        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.messageKey()).isEqualTo(posted.id().toString());

        var payload = objectMapper.readTree(event.payload());
        assertThat(payload.path("transactionId").asText()).isEqualTo(posted.id().toString());
        assertThat(payload.path("reference").asText()).isEqualTo(posted.reference());
    }

    @Test
    void theEventPayloadShouldCarryNoAmountOrBalance() throws Exception {
        transactions.deposit(new TransactionCommands.Deposit(
                account.id(), new BigDecimal("123456.78"), "VND", "seed"));

        String payload = pending().get(0).payload();

        // Identifiers and facts only: a consumer that needs the amount asks the API, with its own
        // authorization.
        assertThat(payload).doesNotContain("123456.78");
        assertThat(objectMapper.readTree(payload).has("amount")).isFalse();
        assertThat(objectMapper.readTree(payload).has("balance")).isFalse();
    }

    @Test
    void aRejectedTransactionShouldQueueNoEvent() {
        // Nothing deposited, so the withdrawal is refused and rolls back — and the event with it.
        assertThatThrownBy(() -> transactions.withdraw(new TransactionCommands.Withdraw(
                account.id(), new BigDecimal("50.00"), "VND", "too much")))
                .isInstanceOf(TransactionExceptions.TransactionRejectedException.class);

        assertThat(pending()).isEmpty();
    }

    @Test
    void anEventShouldCarryTheRequestsTraceId() {
        com.example.bankcore.common.trace.CorrelationId.set("trace-outbox-1");
        try {
            transactions.deposit(new TransactionCommands.Deposit(
                    account.id(), new BigDecimal("10.00"), "VND", "seed"));
        } finally {
            com.example.bankcore.common.trace.CorrelationId.clear();
        }

        assertThat(pending().get(0).traceId()).isEqualTo("trace-outbox-1");
    }

    @Test
    void publishingShouldRequireATransaction() {
        // MANDATORY propagation: an event written outside a transaction could describe something
        // that was never committed, so it fails rather than being written at all.
        var publisher = new com.example.bankcore.common.events.application.EventPublisher(
                outbox, objectMapper, clock);

        assertThatThrownBy(() -> publisher.publish("t", "k", "Type", java.util.Map.of("a", 1)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void anEventShouldBeHandledOnceHoweverOftenItIsDelivered() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger handled = new AtomicInteger();

        for (int delivery = 0; delivery < 5; delivery++) {
            idempotentConsumer.handle(eventId, "test-consumer", null, ignored -> handled.incrementAndGet());
        }

        assertThat(handled.get()).isEqualTo(1);
    }

    @Test
    void concurrentDeliveriesShouldStillProduceOneEffect() throws Exception {
        UUID eventId = UUID.randomUUID();
        AtomicInteger handled = new AtomicInteger();

        int threads = 8;
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        idempotentConsumer.handle(eventId, "test-consumer", null,
                                ignored -> handled.incrementAndGet());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (RuntimeException expected) {
                        // Losing the claim is fine; doing the work twice is not.
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(handled.get()).isEqualTo(1);
    }

    @Test
    void twoConsumersShouldEachHandleTheSameEvent() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();

        idempotentConsumer.handle(eventId, "consumer-a", null, ignored -> first.incrementAndGet());
        idempotentConsumer.handle(eventId, "consumer-b", null, ignored -> second.incrementAndGet());
        idempotentConsumer.handle(eventId, "consumer-a", null, ignored -> first.incrementAndGet());

        // Deduplication is per consumer: both must see it, neither twice.
        assertThat(first.get()).isEqualTo(1);
        assertThat(second.get()).isEqualTo(1);
    }

    @Test
    void aFailingPublishShouldRetryUntilTheLimitThenPark() {
        OutboxEvent event = OutboxEvent.pending(UUID.randomUUID(), "t", "k", "Type", "{}", null,
                clock.instant());

        OutboxEvent afterOne = event.failed("broker down", 3, clock.instant());
        OutboxEvent afterTwo = afterOne.failed("broker down", 3, clock.instant());
        OutboxEvent afterThree = afterTwo.failed("broker down", 3, clock.instant());

        // Still retryable while a transient failure might clear...
        assertThat(afterOne.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(afterTwo.status()).isEqualTo(OutboxStatus.PENDING);
        // ...then parked for a human rather than retried forever.
        assertThat(afterThree.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(afterThree.attempts()).isEqualTo(3);
        assertThat(afterThree.lastError()).isEqualTo("broker down");
    }

    @Test
    void publishedEventsShouldLeaveThePendingQueue() {
        transactions.deposit(new TransactionCommands.Deposit(
                account.id(), new BigDecimal("10.00"), "VND", "seed"));

        OutboxEvent event = pending().get(0);
        outbox.save(event.published(clock.instant()));

        assertThat(pending()).isEmpty();
        assertThat(outbox.countByStatus(OutboxStatus.PUBLISHED)).isEqualTo(1);
    }
}
