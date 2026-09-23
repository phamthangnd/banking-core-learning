package com.example.bankcore.ledger.domain;

import com.example.bankcore.common.money.Money;
import com.example.bankcore.transaction.domain.TransactionDirection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The ledger invariant: debits equal credits, per currency. */
class LedgerEntriesTest {

    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");
    private static final UUID TRANSACTION = UUID.randomUUID();

    private static LedgerEntry entry(TransactionDirection direction, String amount, String currency) {
        return LedgerEntry.forAccount(UUID.randomUUID(), TRANSACTION, 0, UUID.randomUUID(),
                direction, Money.of(amount, currency), Money.of("0", currency), NOW);
    }

    @Test
    void shouldAcceptABalancedPair() {
        assertThatCode(() -> LedgerEntries.requireBalanced(List.of(
                entry(TransactionDirection.DEBIT, "100.00", "VND"),
                entry(TransactionDirection.CREDIT, "100.00", "VND"))))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptSeveralLegsThatStillBalance() {
        assertThatCode(() -> LedgerEntries.requireBalanced(List.of(
                entry(TransactionDirection.DEBIT, "100.00", "VND"),
                entry(TransactionDirection.CREDIT, "70.00", "VND"),
                entry(TransactionDirection.CREDIT, "30.00", "VND"))))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAGroupThatDoesNotBalance() {
        assertThatThrownBy(() -> LedgerEntries.requireBalanced(List.of(
                entry(TransactionDirection.DEBIT, "100.00", "VND"),
                entry(TransactionDirection.CREDIT, "99.99", "VND"))))
                .isInstanceOf(UnbalancedLedgerException.class)
                .hasMessageContaining("does not balance");
    }

    @Test
    void shouldRejectASingleEntry() {
        // A one-sided entry is exactly how money appears from nowhere.
        assertThatThrownBy(() -> LedgerEntries.requireBalanced(List.of(
                entry(TransactionDirection.CREDIT, "100.00", "VND"))))
                .isInstanceOf(UnbalancedLedgerException.class);
    }

    @Test
    void shouldRejectAnEmptyGroup() {
        assertThatThrownBy(() -> LedgerEntries.requireBalanced(List.of()))
                .isInstanceOf(UnbalancedLedgerException.class);
    }

    @Test
    void shouldNotNetOffDifferentCurrencies() {
        // 100 VND of debit does not offset 100 USD of credit, however equal the numbers look.
        assertThatThrownBy(() -> LedgerEntries.requireBalanced(List.of(
                entry(TransactionDirection.DEBIT, "100.00", "VND"),
                entry(TransactionDirection.CREDIT, "100.00", "USD"))))
                .isInstanceOf(UnbalancedLedgerException.class)
                .hasMessageContaining("different currencies");
    }

    @Test
    void shouldTotalPerCurrencyAndDirection() {
        List<LedgerEntry> entries = List.of(
                entry(TransactionDirection.DEBIT, "60.00", "VND"),
                entry(TransactionDirection.DEBIT, "40.00", "VND"),
                entry(TransactionDirection.CREDIT, "100.00", "VND"));

        assertThat(LedgerEntries.totalsByCurrency(entries, true).get("VND").amount())
                .isEqualByComparingTo("100.00");
        assertThat(LedgerEntries.totalsByCurrency(entries, false).get("VND").amount())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRejectAnEntryBelongingToBothAnAccountAndTheBank() {
        assertThatThrownBy(() -> new LedgerEntry(UUID.randomUUID(), TRANSACTION, 0,
                UUID.randomUUID(), SystemAccount.CASH, TransactionDirection.DEBIT,
                Money.of("1", "VND"), Money.of("1", "VND"), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void shouldRejectANonPositiveEntryAmount() {
        assertThatThrownBy(() -> LedgerEntry.forSystem(UUID.randomUUID(), TRANSACTION, 0,
                SystemAccount.CASH, TransactionDirection.DEBIT, Money.zero("VND"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
