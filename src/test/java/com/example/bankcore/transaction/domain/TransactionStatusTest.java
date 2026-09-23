package com.example.bankcore.transaction.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING, POSTED",
            "PENDING, FAILED",
            "POSTED, REVERSED"
    })
    void shouldAllowLegalTransitions(TransactionStatus from, TransactionStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
        assertThat(from.transitionTo(to)).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, REVERSED",
            "PENDING, PENDING",
            "POSTED, PENDING",
            "POSTED, FAILED",
            "FAILED, POSTED",
            "FAILED, PENDING",
            "REVERSED, POSTED",
            "REVERSED, PENDING"
    })
    void shouldRejectIllegalTransitions(TransactionStatus from, TransactionStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();

        assertThatThrownBy(() -> from.transitionTo(to))
                .isInstanceOf(IllegalTransactionTransitionException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @Test
    void shouldTreatFailedAndReversedAsTerminal() {
        assertThat(TransactionStatus.FAILED.isTerminal()).isTrue();
        assertThat(TransactionStatus.REVERSED.isTerminal()).isTrue();
        assertThat(TransactionStatus.PENDING.isTerminal()).isFalse();
        assertThat(TransactionStatus.POSTED.isTerminal()).isFalse();
    }

    @Test
    void shouldNeverReopenAPostedTransactionForEditing() {
        // A posted transaction is immutable: the only way out is a compensating reversal.
        assertThat(TransactionStatus.POSTED.allowedNextStates())
                .containsExactly(TransactionStatus.REVERSED);
    }

    @ParameterizedTest
    @EnumSource(TransactionStatus.class)
    void shouldRejectNullTarget(TransactionStatus from) {
        assertThat(from.canTransitionTo(null)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TransactionStatus.class)
    void shouldNotLeakMutableTransitionTable(TransactionStatus status) {
        var transitions = status.allowedNextStates();
        transitions.clear();

        assertThat(status.allowedNextStates()).isEqualTo(status.allowedNextStates());
        assertThat(status.allowedNextStates().size()).isEqualTo(
                switch (status) {
                    case PENDING -> 2;
                    case POSTED -> 1;
                    case FAILED, REVERSED -> 0;
                });
    }

    @Test
    void shouldFlipDirection() {
        assertThat(TransactionDirection.DEBIT.opposite()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(TransactionDirection.CREDIT.opposite()).isEqualTo(TransactionDirection.DEBIT);
    }
}
