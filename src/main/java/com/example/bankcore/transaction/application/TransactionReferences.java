package com.example.bankcore.transaction.application;

import com.example.bankcore.transaction.domain.TransactionReferenceGenerator;
import com.example.bankcore.transaction.domain.TransactionRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Hands out the next customer-facing transaction reference. */
@Component
public class TransactionReferences {

    private final TransactionRepository transactions;
    private final Clock clock;

    public TransactionReferences(TransactionRepository transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    public String next() {
        return TransactionReferenceGenerator.generate(clock.instant(), transactions.nextReferenceSequence());
    }
}
