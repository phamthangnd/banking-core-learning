package com.example.bankcore.transaction.domain;

import com.example.bankcore.common.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRecordTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:15:30Z");

    private static TransactionRecord pending(String amount) {
        return new TransactionRecord(
                UUID.randomUUID(),
                "ACC-1",
                Money.of(amount, "USD"),
                TransactionDirection.CREDIT,
                TransactionStatus.PENDING,
                NOW,
                "salary");
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> new TransactionRecord(
                UUID.randomUUID(), "ACC-1", Money.of("-1.00", "USD"), TransactionDirection.DEBIT,
                TransactionStatus.PENDING, NOW, "bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direction");
    }

    @Test
    void shouldRejectMissingAccount() {
        assertThatThrownBy(() -> new TransactionRecord(
                UUID.randomUUID(), null, Money.of("1.00", "USD"), TransactionDirection.DEBIT,
                TransactionStatus.PENDING, NOW, "bad"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("accountId");
    }

    @Test
    void shouldProduceNewInstanceOnStatusChange() {
        var original = pending("100.00");

        var posted = original.withStatus(TransactionStatus.POSTED);

        assertThat(original.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(posted.status()).isEqualTo(TransactionStatus.POSTED);
        assertThat(posted.id()).isEqualTo(original.id());
        assertThat(posted.amount()).isEqualTo(original.amount());
        assertThat(posted.isPosted()).isTrue();
    }

    @Test
    void shouldRefuseIllegalStatusChange() {
        var original = pending("100.00");

        assertThatThrownBy(() -> original.withStatus(TransactionStatus.REVERSED))
                .isInstanceOf(IllegalTransactionTransitionException.class);
    }

    @Test
    void shouldExposeCurrencyOfAmount() {
        assertThat(pending("1.00").currency()).isEqualTo("USD");
    }
}
