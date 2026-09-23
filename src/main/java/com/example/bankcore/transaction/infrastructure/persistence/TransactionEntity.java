package com.example.bankcore.transaction.infrastructure.persistence;

import com.example.bankcore.common.money.Money;
import com.example.bankcore.transaction.domain.Transaction;
import com.example.bankcore.transaction.domain.TransactionStatus;
import com.example.bankcore.transaction.domain.TransactionType;
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

/**
 * JPA mapping of the {@code transactions} table.
 *
 * <p>Every column except {@code status} and {@code posted_at} is {@code updatable = false}.
 * Hibernate therefore leaves them out of any UPDATE it generates, which means a careless
 * {@code save} of a modified record cannot rewrite history — and the database trigger catches
 * anything that gets past that.
 */
@Entity
@Table(name = "transactions")
public class TransactionEntity {

    private static final int MONEY_SCALE = 4;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reference", nullable = false, length = 30, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20, updatable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "currency", nullable = false, length = 3, updatable = false, columnDefinition = "bpchar")
    private String currency;

    @Column(name = "amount", nullable = false, precision = 19, scale = MONEY_SCALE, updatable = false)
    private BigDecimal amount;

    @Column(name = "source_account_id", updatable = false)
    private UUID sourceAccountId;

    @Column(name = "target_account_id", updatable = false)
    private UUID targetAccountId;

    @Column(name = "source_balance_after", precision = 19, scale = MONEY_SCALE, updatable = false)
    private BigDecimal sourceBalanceAfter;

    @Column(name = "target_balance_after", precision = 19, scale = MONEY_SCALE, updatable = false)
    private BigDecimal targetBalanceAfter;

    @Column(name = "description", length = 255, updatable = false)
    private String description;

    @Column(name = "failure_reason", length = 255, updatable = false)
    private String failureReason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TransactionEntity() {
    }

    private TransactionEntity(Transaction transaction) {
        this.id = transaction.id();
        this.reference = transaction.reference();
        this.transactionType = transaction.type();
        this.currency = transaction.currency();
        this.amount = scaled(transaction.amount());
        this.sourceAccountId = transaction.sourceAccountId();
        this.targetAccountId = transaction.targetAccountId();
        this.sourceBalanceAfter = scaled(transaction.sourceBalanceAfter());
        this.targetBalanceAfter = scaled(transaction.targetBalanceAfter());
        this.description = transaction.description();
        this.failureReason = transaction.failureReason();
        this.occurredAt = transaction.occurredAt();
        this.createdAt = transaction.createdAt();
        applyState(transaction);
    }

    static TransactionEntity fromDomain(Transaction transaction) {
        return new TransactionEntity(transaction);
    }

    /** Only the mutable part: the status and the moment it was posted. */
    void applyState(Transaction transaction) {
        this.status = transaction.status();
        this.postedAt = transaction.postedAt();
    }

    Transaction toDomain() {
        return new Transaction(id, reference, transactionType, status,
                new Money(amount, currency), sourceAccountId, targetAccountId,
                money(sourceBalanceAfter), money(targetBalanceAfter),
                description, failureReason, occurredAt, postedAt, createdAt);
    }

    private Money money(BigDecimal value) {
        return value == null ? null : new Money(value, currency);
    }

    private static BigDecimal scaled(Money money) {
        return money == null ? null : money.amount().setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
