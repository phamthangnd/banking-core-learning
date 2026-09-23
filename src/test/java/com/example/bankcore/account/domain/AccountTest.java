package com.example.bankcore.account.domain;

import com.example.bankcore.common.money.Money;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The account invariants. These are the banking rules from CLAUDE.md section 3 expressed as a
 * type, so a service cannot forget one.
 */
class AccountTest {

    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);
    private static final String CURRENCY = "VND";

    private static Account open(AccountType type, String overdraft) {
        return Account.open(UUID.randomUUID(), "90040000000018", UUID.randomUUID(), type,
                CURRENCY, Money.of(overdraft, CURRENCY), NOW);
    }

    private static Account active(AccountType type, String overdraft) {
        return open(type, overdraft).activate(NOW);
    }

    @Nested
    class Opening {

        @Test
        void shouldStartPendingWithAZeroBalance() {
            Account account = open(AccountType.CHECKING, "0");

            assertThat(account.status()).isEqualTo(AccountStatus.PENDING);
            assertThat(account.balance().isZero()).isTrue();
            assertThat(account.canTransact()).isFalse();
            assertThat(account.activatedAt()).isNull();
        }

        @Test
        void shouldRefuseAnOverdraftOnATypeThatMayNotGoNegative() {
            assertThatThrownBy(() -> open(AccountType.SAVINGS, "100"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never go negative");
        }

        @Test
        void shouldRefuseANegativeOverdraftLimit() {
            assertThatThrownBy(() -> open(AccountType.CHECKING, "-100"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRefuseABalanceAndLimitInDifferentCurrencies() {
            assertThatThrownBy(() -> new Account(UUID.randomUUID(), "9004", UUID.randomUUID(),
                    AccountType.CHECKING, Money.of("0", "VND"), Money.of("0", "USD"),
                    AccountStatus.PENDING, NOW, null, null, NOW, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("currency");
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void shouldActivateAndRecordWhen() {
            Account account = open(AccountType.CHECKING, "0").activate(LATER);

            assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(account.canTransact()).isTrue();
            assertThat(account.activatedAt()).isEqualTo(LATER);
        }

        @Test
        void shouldKeepTheFirstActivationTimestampAcrossAFreezeCycle() {
            Account account = open(AccountType.CHECKING, "0").activate(NOW)
                    .freeze(LATER)
                    .unfreeze(LATER.plusSeconds(60));

            assertThat(account.activatedAt()).isEqualTo(NOW);
            assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void shouldNotTransactWhileFrozen() {
            Account frozen = active(AccountType.CHECKING, "0").freeze(LATER);

            assertThat(frozen.canTransact()).isFalse();
            assertThatThrownBy(() -> frozen.credit(Money.of("10", CURRENCY), LATER))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class)
                    .hasMessageContaining("FROZEN");
        }

        @Test
        void shouldRefuseIllegalTransitions() {
            Account pending = open(AccountType.CHECKING, "0");

            assertThatThrownBy(() -> pending.freeze(LATER))
                    .isInstanceOf(AccountExceptions.IllegalAccountTransitionException.class);
        }

        @Test
        void shouldCloseOnlyWithAZeroBalance() {
            Account funded = active(AccountType.CHECKING, "0").credit(Money.of("100", CURRENCY), LATER);

            assertThatThrownBy(() -> funded.close(LATER))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class)
                    .hasMessageContaining("zero balance");

            Account emptied = funded.debit(Money.of("100", CURRENCY), LATER);
            assertThat(emptied.close(LATER).status()).isEqualTo(AccountStatus.CLOSED);
        }

        @Test
        void shouldBeIdempotentWhenClosingTwice() {
            Account closed = active(AccountType.CHECKING, "0").close(LATER);

            assertThat(closed.close(LATER.plusSeconds(60))).isSameAs(closed);
            assertThat(closed.closedAt()).isEqualTo(LATER);
        }

        @Test
        void shouldNeverReopenAClosedAccount() {
            Account closed = active(AccountType.CHECKING, "0").close(LATER);

            assertThat(closed.status().isTerminal()).isTrue();
            assertThatThrownBy(() -> closed.activate(LATER))
                    .isInstanceOf(AccountExceptions.IllegalAccountTransitionException.class);
        }

        @Test
        void shouldNotTransactOnAClosedAccount() {
            Account closed = active(AccountType.CHECKING, "0").close(LATER);

            assertThatThrownBy(() -> closed.credit(Money.of("1", CURRENCY), LATER))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class);
        }
    }

    @Nested
    class Balances {

        @Test
        void shouldCreditAndDebit() {
            Account account = active(AccountType.CHECKING, "0")
                    .credit(Money.of("100.50", CURRENCY), LATER)
                    .debit(Money.of("40.25", CURRENCY), LATER);

            assertThat(account.balance().amount()).isEqualByComparingTo("60.25");
        }

        @Test
        void shouldRefuseToGoBelowZeroWithoutAnOverdraft() {
            Account account = active(AccountType.SAVINGS, "0").credit(Money.of("50", CURRENCY), LATER);

            assertThatThrownBy(() -> account.debit(Money.of("50.01", CURRENCY), LATER))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class)
                    .hasMessageContaining("Insufficient");
        }

        @Test
        void shouldAllowGoingNegativeWithinAGrantedOverdraft() {
            Account account = active(AccountType.CHECKING, "200").credit(Money.of("50", CURRENCY), LATER);

            Account overdrawn = account.debit(Money.of("250", CURRENCY), LATER);

            assertThat(overdrawn.balance().amount()).isEqualByComparingTo("-200");
            assertThat(overdrawn.availableBalance().isZero()).isTrue();
        }

        @Test
        void shouldRefuseToGoBeyondTheOverdraft() {
            Account account = active(AccountType.CHECKING, "200");

            assertThatThrownBy(() -> account.debit(Money.of("200.01", CURRENCY), LATER))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class);
        }

        @Test
        void shouldReportAvailableBalanceSeparatelyFromTheBalance() {
            Account account = active(AccountType.CHECKING, "200").credit(Money.of("50", CURRENCY), LATER);

            assertThat(account.balance().amount()).isEqualByComparingTo("50");
            assertThat(account.availableBalance().amount()).isEqualByComparingTo("250");
        }

        @Test
        void shouldRefuseAnAmountInAnotherCurrency() {
            Account account = active(AccountType.CHECKING, "0");

            assertThatThrownBy(() -> account.credit(Money.of("10", "USD"), LATER))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class)
                    .hasMessageContaining("USD");
        }

        @Test
        void shouldRefuseANonPositiveAmount() {
            Account account = active(AccountType.CHECKING, "0");

            assertThatThrownBy(() -> account.credit(Money.zero(CURRENCY), LATER))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class);
            assertThatThrownBy(() -> account.debit(Money.of("-5", CURRENCY), LATER))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class);
        }

        @Test
        void shouldNotLoseCentsToFloatingPoint() {
            Account account = active(AccountType.CHECKING, "0");
            for (int i = 0; i < 10; i++) {
                account = account.credit(Money.of("0.10", CURRENCY), LATER);
            }

            assertThat(account.balance().amount()).isEqualByComparingTo("1.00");
        }
    }

    @Nested
    class OverdraftLimit {

        @Test
        void shouldGrantAnOverdraftOnACheckingAccount() {
            Account account = active(AccountType.CHECKING, "0")
                    .withOverdraftLimit(Money.of("500", CURRENCY), LATER);

            assertThat(account.overdraftLimit().amount()).isEqualByComparingTo("500");
        }

        @Test
        void shouldRefuseAnOverdraftOnASavingsAccount() {
            Account account = active(AccountType.SAVINGS, "0");

            assertThatThrownBy(() -> account.withOverdraftLimit(Money.of("1", CURRENCY), LATER))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class);
        }

        @Test
        void shouldRefuseToLowerTheLimitBelowAnExistingDebt() {
            Account overdrawn = active(AccountType.CHECKING, "500")
                    .debit(Money.of("400", CURRENCY), LATER);

            // Lowering the limit to 100 would put the account in breach retroactively.
            assertThatThrownBy(() -> overdrawn.withOverdraftLimit(Money.of("100", CURRENCY), LATER))
                    .isInstanceOf(AccountExceptions.AccountRuleViolationException.class)
                    .hasMessageContaining("already breached");
        }
    }
}
