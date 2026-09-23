package com.example.bankcore.common.money;

/**
 * Thrown when two {@link Money} values of different currencies are combined.
 *
 * <p>Money arithmetic across currencies is never a rounding problem, it is a modelling bug:
 * it must fail loudly instead of producing a plausible-looking wrong number.
 */
public class CurrencyMismatchException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public CurrencyMismatchException(String left, String right) {
        super("currency mismatch: %s vs %s".formatted(left, right));
    }
}
