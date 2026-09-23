package com.example.bankcore.account.infrastructure.persistence;

import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountStatus;
import com.example.bankcore.account.domain.AccountType;
import com.example.bankcore.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping of the {@code accounts} table.
 *
 * <p>{@link Money} is split into a {@code NUMERIC} amount and a currency column rather than being
 * mapped as an embeddable, so both parts are visible to SQL: reports group by currency, and the
 * {@code balance >= -overdraft_limit} check constraint needs the amount as a plain column.
 *
 * <p>The scale is fixed at four decimals to match the column. Letting Hibernate write whatever
 * scale an in-memory {@link BigDecimal} happens to carry would make stored values differ from
 * what the domain computed.
 */
@Entity
@Table(name = "accounts")
public class AccountEntity {

    /** Must match {@code NUMERIC(19, 4)} in the migration. */
    private static final int MONEY_SCALE = 4;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_number", nullable = false, length = 20, updatable = false)
    private String accountNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20, updatable = false)
    private AccountType accountType;

    @Column(name = "currency", nullable = false, length = 3, updatable = false, columnDefinition = "bpchar")
    private String currency;

    @Column(name = "balance", nullable = false, precision = 19, scale = MONEY_SCALE)
    private BigDecimal balance;

    @Column(name = "overdraft_limit", nullable = false, precision = 19, scale = MONEY_SCALE)
    private BigDecimal overdraftLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AccountEntity() {
    }

    private AccountEntity(Account account) {
        this.id = account.id();
        this.accountNumber = account.accountNumber();
        this.customerId = account.customerId();
        this.accountType = account.accountType();
        this.currency = account.currency();
        this.openedAt = account.openedAt();
        this.createdAt = account.createdAt();
        applyState(account);
    }

    static AccountEntity fromDomain(Account account) {
        return new AccountEntity(account);
    }

    void applyState(Account account) {
        this.balance = account.balance().amount().setScale(MONEY_SCALE, java.math.RoundingMode.UNNECESSARY);
        this.overdraftLimit = account.overdraftLimit().amount()
                .setScale(MONEY_SCALE, java.math.RoundingMode.UNNECESSARY);
        this.status = account.status();
        this.activatedAt = account.activatedAt();
        this.closedAt = account.closedAt();
        this.updatedAt = account.updatedAt();
    }

    Account toDomain() {
        return new Account(id, accountNumber, customerId, accountType,
                new Money(balance, currency), new Money(overdraftLimit, currency),
                status, openedAt, activatedAt, closedAt, createdAt, updatedAt);
    }

    public UUID getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }
}
