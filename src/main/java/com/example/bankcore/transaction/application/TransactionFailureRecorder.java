package com.example.bankcore.transaction.application;

import com.example.bankcore.common.money.Money;
import com.example.bankcore.transaction.domain.Transaction;
import com.example.bankcore.transaction.domain.TransactionRepository;
import com.example.bankcore.transaction.domain.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Records a rejected movement so it survives the rollback that rejects it.
 *
 * <p>The same trap as the failed-login counter in Phase 03, and it matters more here: a
 * transfer that was refused is part of an account's history, and "why did my payment not go
 * through" has to be answerable. Writing the FAILED row inside the caller's transaction would
 * roll it back together with the refusal, leaving no trace at all — a financial operation
 * failing silently, which CLAUDE.md section 3 forbids.
 *
 * <p>{@link Propagation#REQUIRES_NEW} commits it separately. It has to live in its own bean,
 * because Spring's proxy-based transactions ignore a self-invocation.
 */
@Component
public class TransactionFailureRecorder {

    private static final Logger log = LoggerFactory.getLogger(TransactionFailureRecorder.class);

    private final TransactionRepository transactions;
    private final TransactionReferences references;
    private final Clock clock;

    public TransactionFailureRecorder(TransactionRepository transactions,
                                      TransactionReferences references, Clock clock) {
        this.transactions = transactions;
        this.references = references;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction record(TransactionType type, Money amount, UUID sourceAccountId,
                              UUID targetAccountId, String description, String reason) {
        Transaction failed = Transaction.failed(UUID.randomUUID(), references.next(),
                type, amount, sourceAccountId, targetAccountId, description, reason, clock.instant());

        Transaction saved = transactions.save(failed);
        log.info("Transaction rejected: reference={} type={} reason={}",
                saved.reference(), type, reason);
        return saved;
    }
}
