package com.example.bankcore.common.money;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Nested
    class Construction {

        @Test
        void shouldRejectNullAmount() {
            assertThatThrownBy(() -> new Money(null, "USD"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("amount");
        }

        @Test
        void shouldRejectBlankCurrency() {
            assertThatThrownBy(() -> new Money(BigDecimal.ONE, " "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("currency");
        }

        @Test
        void shouldRejectUnknownCurrency() {
            assertThatThrownBy(() -> Money.of("10.00", "XYZ"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ISO-4217");
        }

        @Test
        void shouldRejectScaleBeyondFourDecimals() {
            assertThatThrownBy(() -> Money.of("1.000005", "USD"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("scale");
        }

        @Test
        void shouldBuildZero() {
            assertThat(Money.zero("VND").isZero()).isTrue();
            assertThat(Money.zero("VND").currency()).isEqualTo("VND");
        }
    }

    @Nested
    class Arithmetic {

        @Test
        void shouldAddSameCurrency() {
            var first = new Money(new BigDecimal("100.00"), "USD");
            var second = new Money(new BigDecimal("25.00"), "USD");

            var result = first.add(second);

            assertThat(result.amount()).isEqualByComparingTo("125.00");
            assertThat(result.currency()).isEqualTo("USD");
        }

        @Test
        void shouldSubtractSameCurrency() {
            var result = Money.of("100.00", "USD").subtract(Money.of("30.50", "USD"));

            assertThat(result.amount()).isEqualByComparingTo("69.50");
        }

        @Test
        void shouldAllowNegativeResultForSubtraction() {
            var result = Money.of("10.00", "USD").subtract(Money.of("25.00", "USD"));

            assertThat(result.isNegative()).isTrue();
            assertThat(result.abs().amount()).isEqualByComparingTo("15.00");
        }

        @Test
        void shouldRejectMixingCurrencies() {
            var usd = Money.of("10.00", "USD");
            var eur = Money.of("10.00", "EUR");

            assertThatThrownBy(() -> usd.add(eur))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessageContaining("USD")
                    .hasMessageContaining("EUR");

            assertThatThrownBy(() -> usd.subtract(eur))
                    .isInstanceOf(CurrencyMismatchException.class);

            assertThatThrownBy(() -> usd.compareTo(eur))
                    .isInstanceOf(CurrencyMismatchException.class);
        }

        @Test
        void shouldMultiplyWithBankersRounding() {
            // 2.345 rounds to the even neighbour 2.34, not up to 2.35.
            var result = Money.of("4.69", "USD").multiply(new BigDecimal("0.5"));

            assertThat(result.amount()).isEqualByComparingTo("2.34");
            assertThat(result.amount().scale()).isEqualTo(2);
        }

        @Test
        void shouldNegate() {
            assertThat(Money.of("10.00", "USD").negate().amount()).isEqualByComparingTo("-10.00");
        }

        @Test
        void shouldNotUseBinaryFloatingPoint() {
            // The classic double bug: 0.1 + 0.2 != 0.3. BigDecimal keeps the decimal value exact.
            var result = Money.of("0.10", "USD").add(Money.of("0.20", "USD"));

            assertThat(result.amount()).isEqualByComparingTo("0.30");
            assertThat(result.amount().toPlainString()).isEqualTo("0.30");
        }
    }

    @Nested
    class Aggregation {

        @Test
        void shouldSumValues() {
            var total = Money.sum("USD", List.of(
                    Money.of("10.00", "USD"),
                    Money.of("5.25", "USD"),
                    Money.of("0.75", "USD")));

            assertThat(total.amount()).isEqualByComparingTo("16.00");
        }

        @Test
        void shouldSumEmptyCollectionToZero() {
            assertThat(Money.sum("USD", List.of()).isZero()).isTrue();
        }

        @Test
        void shouldRejectSumOfMixedCurrencies() {
            var values = List.of(Money.of("10.00", "USD"), Money.of("10.00", "EUR"));

            assertThatThrownBy(() -> Money.sum("USD", values))
                    .isInstanceOf(CurrencyMismatchException.class);
        }
    }

    @Nested
    class Comparison {

        @Test
        void shouldCompareByNumericValueNotScale() {
            var ten = Money.of("10.0", "USD");
            var tenWithMoreDecimals = Money.of("10.00", "USD");

            // equals() delegates to BigDecimal.equals(), which also compares the scale...
            assertThat(ten).isNotEqualTo(tenWithMoreDecimals);
            // ...so amount comparisons must go through compareTo().
            assertThat(ten).isEqualByComparingTo(tenWithMoreDecimals);
        }

        @Test
        void shouldOrderValues() {
            assertThat(Money.of("5.00", "USD")).isLessThan(Money.of("7.00", "USD"));
            assertThat(Money.of("9.00", "USD")).isGreaterThan(Money.of("7.00", "USD"));
        }

        @Test
        void shouldExposeSign() {
            assertThat(Money.of("1.00", "USD").isPositive()).isTrue();
            assertThat(Money.of("-1.00", "USD").isNegative()).isTrue();
            assertThat(Money.of("0.00", "USD").isZero()).isTrue();
        }
    }
}
