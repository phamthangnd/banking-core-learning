package com.example.bankcore.transaction.domain;

import com.example.bankcore.common.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");
    private static final Money AMOUNT = Money.of("100.00", "VND");
    private static final UUID SOURCE = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    private static Transaction posted(TransactionType type, UUID source, UUID target) {
        return Transaction.posted(UUID.randomUUID(), "TXN-20260615-000000001", type, AMOUNT,
                source, target, source == null ? null : Money.of("900.00", "VND"),
                target == null ? null : Money.of("1100.00", "VND"), "test", NOW);
    }

    @Test
    void depositShouldHaveATargetAndNoSource() {
        Transaction deposit = posted(TransactionType.DEPOSIT, null, TARGET);

        assertThat(deposit.isPosted()).isTrue();
        assertThat(deposit.sourceAccountId()).isNull();
        assertThat(deposit.targetAccountId()).isEqualTo(TARGET);
        assertThat(deposit.postedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRejectADepositWithASource() {
        assertThatThrownBy(() -> posted(TransactionType.DEPOSIT, SOURCE, TARGET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }

    @Test
    void shouldRejectAWithdrawalWithATarget() {
        assertThatThrownBy(() -> posted(TransactionType.WITHDRAWAL, SOURCE, TARGET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target");
    }

    @Test
    void shouldRejectATransferToTheSameAccount() {
        assertThatThrownBy(() -> posted(TransactionType.TRANSFER, SOURCE, SOURCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two different accounts");
    }

    @Test
    void shouldRejectANonPositiveAmount() {
        assertThatThrownBy(() -> Transaction.posted(UUID.randomUUID(), "TXN-1", TransactionType.DEPOSIT,
                Money.zero("VND"), null, TARGET, null, Money.zero("VND"), "test", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void failedTransactionShouldCarryAReasonAndNoBalances() {
        Transaction failed = Transaction.failed(UUID.randomUUID(), "TXN-2", TransactionType.WITHDRAWAL,
                AMOUNT, SOURCE, null, "test", "Insufficient available balance", NOW);

        assertThat(failed.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(failed.isPosted()).isFalse();
        assertThat(failed.postedAt()).isNull();
        assertThat(failed.sourceBalanceAfter()).isNull();
        assertThat(failed.failureReason()).isEqualTo("Insufficient available balance");
    }

    @Test
    void shouldReportWhichAccountsItTouches() {
        Transaction transfer = posted(TransactionType.TRANSFER, SOURCE, TARGET);

        assertThat(transfer.touches(SOURCE)).isTrue();
        assertThat(transfer.touches(TARGET)).isTrue();
        assertThat(transfer.touches(UUID.randomUUID())).isFalse();
    }

    @Test
    void shouldOnlyAllowLifecycleStatusChanges() {
        Transaction failed = Transaction.failed(UUID.randomUUID(), "TXN-3", TransactionType.DEPOSIT,
                AMOUNT, null, TARGET, "test", "rejected", NOW);

        // FAILED is terminal: a rejected movement is never quietly turned into a successful one.
        assertThatThrownBy(() -> failed.withStatus(TransactionStatus.POSTED, NOW))
                .isInstanceOf(IllegalTransactionTransitionException.class);
    }

    @Test
    void shouldAllowReversalOfAPostedTransaction() {
        Transaction reversed = posted(TransactionType.TRANSFER, SOURCE, TARGET)
                .withStatus(TransactionStatus.REVERSED, NOW.plusSeconds(60));

        // The amount and the accounts are untouched; only the status moved.
        assertThat(reversed.status()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(reversed.amount()).isEqualTo(AMOUNT);
        assertThat(reversed.sourceAccountId()).isEqualTo(SOURCE);
    }
}
