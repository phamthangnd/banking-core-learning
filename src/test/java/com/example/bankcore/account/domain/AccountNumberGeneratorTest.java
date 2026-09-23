package com.example.bankcore.account.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountNumberGeneratorTest {

    @Test
    void shouldBuildANumberFromPrefixSequenceAndCheckDigit() {
        String number = AccountNumberGenerator.generate("9004", 1_000_001);

        assertThat(number).hasSize(14).startsWith("9004").matches("[0-9]+");
        assertThat(AccountNumberGenerator.isValid(number)).isTrue();
    }

    @Test
    void shouldPadTheSequenceToAFixedWidth() {
        assertThat(AccountNumberGenerator.generate("9004", 1)).startsWith("9004000000001");
    }

    @Test
    void shouldProduceADistinctNumberForEverySequenceValue() {
        Set<String> numbers = new HashSet<>();
        for (long sequence = 1; sequence <= 1000; sequence++) {
            numbers.add(AccountNumberGenerator.generate("9004", sequence));
        }

        assertThat(numbers).hasSize(1000);
    }

    @Test
    void shouldRejectANumberWithASingleWrongDigit() {
        String number = AccountNumberGenerator.generate("9004", 1_000_001);

        // The whole point of the check digit: a typo must not silently address another account.
        for (int position = 0; position < number.length() - 1; position++) {
            char original = number.charAt(position);
            char replacement = original == '9' ? '8' : (char) (original + 1);
            String typo = number.substring(0, position) + replacement + number.substring(position + 1);

            assertThat(AccountNumberGenerator.isValid(typo))
                    .describedAs("typo at position %d: %s", position, typo)
                    .isFalse();
        }
    }

    @Test
    void shouldRejectMostAdjacentTranspositions() {
        String number = AccountNumberGenerator.generate("9004", 123_456);
        int caught = 0;
        int transpositions = 0;

        for (int i = 0; i < number.length() - 1; i++) {
            if (number.charAt(i) == number.charAt(i + 1)) {
                continue;
            }
            transpositions++;

            String swapped = number.substring(0, i) + number.charAt(i + 1) + number.charAt(i)
                    + number.substring(i + 2);
            if (!AccountNumberGenerator.isValid(swapped)) {
                caught++;
            }
        }

        // Luhn misses only the 09 <-> 90 swap, so it catches the overwhelming majority.
        assertThat(caught).isGreaterThanOrEqualTo(transpositions - 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "12a4", "1"})
    void shouldRejectMalformedNumbers(String candidate) {
        assertThat(AccountNumberGenerator.isValid(candidate)).isFalse();
    }

    @Test
    void shouldRejectNullNumber() {
        assertThat(AccountNumberGenerator.isValid(null)).isFalse();
    }

    @Test
    void shouldRejectAnInvalidPrefix() {
        assertThatThrownBy(() -> AccountNumberGenerator.generate("ABCD", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountNumberGenerator.generate("", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectANegativeSequence() {
        assertThatThrownBy(() -> AccountNumberGenerator.generate("9004", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
