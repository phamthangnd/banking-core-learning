package com.example.bankcore.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable monetary amount with an explicit currency.
 *
 * <p>Design rules (CLAUDE.md section 3):
 * <ul>
 *   <li>amounts are {@link BigDecimal}, never {@code double} or {@code float}</li>
 *   <li>currency is always explicit and validated against ISO-4217</li>
 *   <li>every operation returns a new value; a {@code Money} is never mutated</li>
 *   <li>mixing currencies throws instead of silently producing a wrong result</li>
 * </ul>
 *
 * <p>Java note: this is a {@code record}. The canonical constructor is generated, and the
 * compact constructor below runs before the fields are assigned, which makes it the single
 * place where the invariants of the type are enforced.
 */
public record Money(BigDecimal amount, String currency) implements Comparable<Money> {

    /** Maximum number of decimal places an amount may carry before rounding is required. */
    public static final int MAX_SCALE = 4;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");

        if (amount.scale() > MAX_SCALE) {
            throw new IllegalArgumentException("amount scale must be <= " + MAX_SCALE);
        }

        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }

        requireIsoCurrency(currency);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    /**
     * Builds a value from its textual representation.
     *
     * <p>Always prefer {@code new BigDecimal("0.10")} over {@code BigDecimal.valueOf(0.10)}:
     * the latter starts from a binary {@code double} that cannot represent 0.10 exactly.
     */
    public static Money of(String amount, String currency) {
        Objects.requireNonNull(amount, "amount must not be null");
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * Sums a collection of values of the given currency.
     *
     * <p>The currency is a parameter rather than being derived from the elements so that an
     * empty collection still produces a well-defined zero instead of an empty {@code Optional}.
     */
    public static Money sum(String currency, Collection<Money> values) {
        Objects.requireNonNull(values, "values must not be null");
        return values.stream().reduce(zero(currency), Money::add);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    /**
     * Multiplies by a factor (interest rate, fee percentage, quantity) and rounds the result
     * back to this value's scale using HALF_EVEN — "banker's rounding", the convention that
     * does not systematically favour one side of a transaction.
     */
    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor must not be null");
        return new Money(amount.multiply(factor).setScale(amount.scale(), RoundingMode.HALF_EVEN), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    /**
     * Compares two values of the same currency by numeric value.
     *
     * <p>Note the difference from {@link #equals(Object)}: the record's generated {@code equals}
     * delegates to {@link BigDecimal#equals(Object)}, which considers 10.0 and 10.00 different
     * because their scales differ. Comparisons of amounts must therefore use this method.
     */
    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!currency.equals(other.currency())) {
            throw new CurrencyMismatchException(currency, other.currency());
        }
    }

    private static void requireIsoCurrency(String currency) {
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown ISO-4217 currency: " + currency, ex);
        }
    }
}
