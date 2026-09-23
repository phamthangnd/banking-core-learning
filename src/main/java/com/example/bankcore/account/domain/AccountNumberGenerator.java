package com.example.bankcore.account.domain;

/**
 * Turns a sequence value into a customer-facing account number.
 *
 * <p>The number is {@code <prefix><zero-padded sequence><check digit>}, for example
 * {@code 900410000019}. Three decisions worth spelling out:
 *
 * <ul>
 *   <li><b>A database sequence supplies the unique part.</b> Generating a random number and
 *       retrying on collision works until two requests collide at the same instant; a sequence
 *       cannot collide, and the database is the only component that can settle this under
 *       concurrency.</li>
 *   <li><b>A check digit is appended.</b> Account numbers are typed, dictated over the phone and
 *       copied by hand. A Luhn digit catches every single-digit error and almost every
 *       transposition of adjacent digits, which turns "money sent to a stranger" into a
 *       validation error.</li>
 *   <li><b>It carries no meaning.</b> No branch code, no customer id, no opening date. Encoding
 *       facts into an identifier means the identifier has to change when the facts do.</li>
 * </ul>
 */
public final class AccountNumberGenerator {

    private static final int SEQUENCE_DIGITS = 9;

    private AccountNumberGenerator() {
    }

    /**
     * @param prefix   bank prefix, digits only
     * @param sequence strictly increasing value from the database sequence
     */
    public static String generate(String prefix, long sequence) {
        if (prefix == null || !prefix.matches("[0-9]{1,6}")) {
            throw new IllegalArgumentException("prefix must be 1 to 6 digits");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }

        // Java's Formatter has no C-style "*" width argument, so the width is baked into the
        // format string.
        String body = prefix + ("%0" + SEQUENCE_DIGITS + "d").formatted(sequence);
        return body + luhnCheckDigit(body);
    }

    /** Whether a number's trailing check digit matches the rest of it. */
    public static boolean isValid(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 2 || !accountNumber.matches("[0-9]+")) {
            return false;
        }

        String body = accountNumber.substring(0, accountNumber.length() - 1);
        char expected = luhnCheckDigit(body);
        return accountNumber.charAt(accountNumber.length() - 1) == expected;
    }

    /**
     * The Luhn (mod 10) check digit: every second digit from the right is doubled, digits above
     * nine have nine subtracted, and the digit that brings the total to a multiple of ten is the
     * answer.
     */
    private static char luhnCheckDigit(String digits) {
        int sum = 0;
        boolean doubleIt = true;

        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';

            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            doubleIt = !doubleIt;
        }

        return (char) ('0' + ((10 - (sum % 10)) % 10));
    }
}
