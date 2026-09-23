package com.example.bankcore.transaction.domain;

import com.example.bankcore.common.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionAnalyticsTest {

    private static final Instant DAY_1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant DAY_2 = DAY_1.plus(Duration.ofDays(1));
    private static final Instant DAY_3 = DAY_1.plus(Duration.ofDays(2));

    private static TransactionRecord record(
            String accountId, String amount, String currency,
            TransactionDirection direction, TransactionStatus status, Instant occurredAt) {
        return new TransactionRecord(UUID.randomUUID(), accountId, Money.of(amount, currency),
                direction, status, occurredAt, "test");
    }

    private final List<TransactionRecord> records = List.of(
            record("ACC-1", "100.00", "USD", TransactionDirection.CREDIT, TransactionStatus.POSTED, DAY_1),
            record("ACC-1", "30.00", "USD", TransactionDirection.DEBIT, TransactionStatus.POSTED, DAY_2),
            record("ACC-1", "500.00", "USD", TransactionDirection.DEBIT, TransactionStatus.PENDING, DAY_2),
            record("ACC-1", "999.00", "USD", TransactionDirection.DEBIT, TransactionStatus.FAILED, DAY_3),
            record("ACC-2", "70.00", "USD", TransactionDirection.CREDIT, TransactionStatus.POSTED, DAY_3),
            record("ACC-1", "40.00", "EUR", TransactionDirection.CREDIT, TransactionStatus.POSTED, DAY_3));

    @Test
    void shouldTotalPostedAmountsPerCurrencyWithoutMixing() {
        var totals = TransactionAnalytics.postedTotalsByCurrency(records);

        assertThat(totals).containsOnlyKeys("EUR", "USD");
        assertThat(totals.get("USD").amount()).isEqualByComparingTo("200.00"); // 100 + 30 + 70
        assertThat(totals.get("EUR").amount()).isEqualByComparingTo("40.00");
    }

    @Test
    void shouldIgnoreNonPostedRecordsInTotals() {
        var onlyPending = List.of(
                record("ACC-1", "10.00", "USD", TransactionDirection.CREDIT, TransactionStatus.PENDING, DAY_1));

        assertThat(TransactionAnalytics.postedTotalsByCurrency(onlyPending)).isEmpty();
    }

    @Test
    void shouldComputeNetPositionAsCreditsMinusDebits() {
        var position = TransactionAnalytics.netPosition(records, "ACC-1", "USD");

        // Only posted entries count: +100 credit, -30 debit. Pending and failed are ignored.
        assertThat(position.amount()).isEqualByComparingTo("70.00");
        assertThat(position.currency()).isEqualTo("USD");
    }

    @Test
    void shouldReturnZeroPositionForUnknownAccount() {
        assertThat(TransactionAnalytics.netPosition(records, "ACC-404", "USD").isZero()).isTrue();
    }

    @Test
    void shouldFindLargestPostedTransaction() {
        var largest = TransactionAnalytics.largestPosted(records, "USD");

        assertThat(largest).isPresent();
        assertThat(largest.get().amount().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldReturnEmptyWhenNoPostedTransactionInCurrency() {
        assertThat(TransactionAnalytics.largestPosted(records, "JPY")).isEmpty();
    }

    @Test
    void shouldFilterByHalfOpenTimeRange() {
        var inRange = TransactionAnalytics.occurredBetween(records, DAY_1, DAY_3);

        // DAY_1 is included, DAY_3 is excluded.
        assertThat(inRange).hasSize(3);
        assertThat(inRange).allSatisfy(record -> assertThat(record.occurredAt()).isBefore(DAY_3));
        assertThat(inRange.get(0).occurredAt()).isEqualTo(DAY_1);
    }

    @Test
    void shouldRejectInvertedRange() {
        assertThatThrownBy(() -> TransactionAnalytics.occurredBetween(records, DAY_3, DAY_1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnMostRecentFirst() {
        var recent = TransactionAnalytics.mostRecent(records, 2);

        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).occurredAt()).isEqualTo(DAY_3);
        assertThat(recent.get(1).occurredAt()).isEqualTo(DAY_3);
    }

    @Test
    void shouldCountEveryStatusIncludingEmptyOnes() {
        var counts = TransactionAnalytics.countByStatus(records);

        assertThat(counts)
                .containsEntry(TransactionStatus.POSTED, 4L)
                .containsEntry(TransactionStatus.PENDING, 1L)
                .containsEntry(TransactionStatus.FAILED, 1L)
                .containsEntry(TransactionStatus.REVERSED, 0L);
    }

    @Test
    void shouldReturnImmutableResults() {
        var recent = TransactionAnalytics.mostRecent(records, 1);

        assertThatThrownBy(() -> recent.add(records.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
