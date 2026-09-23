package com.example.bankcore.transaction.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionReferenceGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-09-23T06:22:00Z");

    @Test
    void shouldCombineTheDateAndTheSequence() {
        assertThat(TransactionReferenceGenerator.generate(NOW, 1234))
                .isEqualTo("TXN-20260923-000001234");
    }

    @Test
    void shouldFormatTheDateInUtc() {
        // 23:30 UTC is already the next day in Asia/Ho_Chi_Minh; the reference must not depend
        // on where the server happens to run.
        assertThat(TransactionReferenceGenerator.generate(Instant.parse("2026-09-23T23:30:00Z"), 1))
                .startsWith("TXN-20260923-");
    }

    @Test
    void shouldRejectANegativeSequence() {
        assertThatThrownBy(() -> TransactionReferenceGenerator.generate(NOW, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
