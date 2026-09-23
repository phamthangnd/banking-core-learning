package com.example.bankcore.ledger.infrastructure.persistence;

import com.example.bankcore.common.money.Money;
import com.example.bankcore.ledger.domain.LedgerEntry;
import com.example.bankcore.ledger.domain.SystemAccount;
import com.example.bankcore.transaction.domain.TransactionDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping of a ledger entry. Every column is insert-only; the table is append-only. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {

    private static final int MONEY_SCALE = 4;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "entry_index", nullable = false, updatable = false)
    private int entryIndex;

    @Column(name = "account_id", updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_account", length = 20, updatable = false)
    private SystemAccount systemAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 6, updatable = false)
    private TransactionDirection direction;

    @Column(name = "currency", nullable = false, length = 3, updatable = false, columnDefinition = "bpchar")
    private String currency;

    @Column(name = "amount", nullable = false, precision = 19, scale = MONEY_SCALE, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 19, scale = MONEY_SCALE, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntryEntity() {
    }

    private LedgerEntryEntity(LedgerEntry entry) {
        this.id = entry.id();
        this.transactionId = entry.transactionId();
        this.entryIndex = entry.entryIndex();
        this.accountId = entry.accountId();
        this.systemAccount = entry.systemAccount();
        this.direction = entry.direction();
        this.currency = entry.currency();
        this.amount = entry.amount().amount().setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        this.balanceAfter = entry.balanceAfter() == null ? null
                : entry.balanceAfter().amount().setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        this.createdAt = entry.createdAt();
    }

    static LedgerEntryEntity fromDomain(LedgerEntry entry) {
        return new LedgerEntryEntity(entry);
    }

    LedgerEntry toDomain() {
        return new LedgerEntry(id, transactionId, entryIndex, accountId, systemAccount, direction,
                new Money(amount, currency),
                balanceAfter == null ? null : new Money(balanceAfter, currency),
                createdAt);
    }
}
