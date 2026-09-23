package com.example.bankcore.common.concurrency;

import com.example.bankcore.common.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryBalanceStoreTest {

    private static final String ACCOUNT = "ACC-1";

    private final InMemoryBalanceStore store = new InMemoryBalanceStore();

    @Test
    void shouldStartWithoutBalance() {
        assertThat(store.balanceOf(ACCOUNT)).isEmpty();
    }

    @Test
    void shouldCreditAndDebit() {
        store.credit(ACCOUNT, Money.of("100.00", "USD"));
        var balance = store.debit(ACCOUNT, Money.of("40.00", "USD"));

        assertThat(balance.amount()).isEqualByComparingTo("60.00");
        assertThat(store.balanceOf(ACCOUNT)).contains(balance);
    }

    @Test
    void shouldRejectOverdraft() {
        store.credit(ACCOUNT, Money.of("10.00", "USD"));

        assertThatThrownBy(() -> store.debit(ACCOUNT, Money.of("10.01", "USD")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining(ACCOUNT);

        // The failed debit must leave the balance untouched.
        assertThat(store.balanceOf(ACCOUNT).orElseThrow().amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldRejectNonPositiveAmounts() {
        assertThatThrownBy(() -> store.credit(ACCOUNT, Money.zero("USD")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.debit(ACCOUNT, Money.of("-5.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNotLoseUpdatesUnderConcurrentCredits() throws Exception {
        int threads = 16;
        int creditsPerThread = 200;
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < creditsPerThread; j++) {
                            store.credit(ACCOUNT, Money.of("1.00", "USD"));
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        var expected = new BigDecimal(threads * creditsPerThread).setScale(2);
        assertThat(store.balanceOf(ACCOUNT).orElseThrow().amount()).isEqualByComparingTo(expected);
    }

    @Test
    void shouldNeverLetConcurrentDebitsDriveTheBalanceNegative() throws Exception {
        store.credit(ACCOUNT, Money.of("100.00", "USD"));

        int threads = 20;
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var succeeded = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        store.debit(ACCOUNT, Money.of("10.00", "USD"));
                        succeeded.incrementAndGet();
                    } catch (InsufficientFundsException expected) {
                        // Losing the race is a legitimate outcome; silently overdrawing is not.
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        // Exactly ten debits of 10.00 fit into a balance of 100.00 - no more, no fewer.
        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(store.balanceOf(ACCOUNT).orElseThrow().isZero()).isTrue();
    }
}
