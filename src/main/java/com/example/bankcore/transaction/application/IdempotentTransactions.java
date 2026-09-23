package com.example.bankcore.transaction.application;

import com.example.bankcore.common.idempotency.IdempotencyExceptions;
import com.example.bankcore.common.idempotency.IdempotencyRecord;
import com.example.bankcore.common.idempotency.IdempotencyRepository;
import com.example.bankcore.transaction.domain.Transaction;
import com.example.bankcore.transaction.domain.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs a money movement at most once per idempotency key.
 *
 * <p>The problem this solves: a client sends a transfer, the connection drops before the
 * response arrives, and the client retries. Without a key the money moves twice, and the client
 * cannot tell the difference between "it did not happen" and "I did not hear about it".
 *
 * <p>The sequence:
 * <ol>
 *   <li><b>Claim</b> the key with an insert that commits immediately. If the insert loses to the
 *       unique index, another attempt owns the key.</li>
 *   <li>If the owner has <b>completed</b> and the request fingerprint matches, return its
 *       transaction — a replay, answered with the original result.</li>
 *   <li>If the fingerprint differs, refuse: the same key was used for a different request, and
 *       answering with the earlier result would tell the caller that the request it just sent had
 *       been carried out.</li>
 *   <li>If the owner is still <b>in progress</b>, refuse: the effect may be about to happen.</li>
 *   <li>Otherwise run the operation. On success record the transaction against the key; on
 *       failure release the key so a legitimate retry is possible.</li>
 * </ol>
 *
 * <p>Both the claim and the release commit in their own transactions, because a claim that is
 * invisible until the end is useless and a release that rolls back with the failure would lock
 * the key forever.
 */
@Component
public class IdempotentTransactions {

    /** Keeps transaction keys from colliding with keys of other operation families later. */
    public static final String SCOPE = "transaction";

    private static final Logger log = LoggerFactory.getLogger(IdempotentTransactions.class);

    private final IdempotencyRepository keys;
    private final TransactionRepository transactions;
    private final Clock clock;

    public IdempotentTransactions(IdempotencyRepository keys, TransactionRepository transactions,
                                  Clock clock) {
        this.keys = keys;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * @param idempotencyKey   client-supplied key, or {@code null} to run without protection
     * @param fingerprint      fingerprint of the request
     * @param operation        the movement to perform
     */
    public Transaction execute(String idempotencyKey, String fingerprint,
                               Supplier<Transaction> operation) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return operation.get();
        }

        Optional<IdempotencyRecord> claimed = keys.claim(IdempotencyRecord.started(
                UUID.randomUUID(), SCOPE, idempotencyKey, fingerprint, clock.instant()));

        if (claimed.isEmpty()) {
            return replayOrRefuse(idempotencyKey, fingerprint);
        }

        IdempotencyRecord record = claimed.get();
        try {
            Transaction result = operation.get();
            keys.complete(record.completedWith(result.id(), clock.instant()));
            return result;
        } catch (RuntimeException ex) {
            // The movement did not happen, so the key must not stay claimed: the client is
            // entitled to retry after fixing whatever was wrong.
            keys.release(record);
            throw ex;
        }
    }

    private Transaction replayOrRefuse(String idempotencyKey, String fingerprint) {
        IdempotencyRecord existing = keys.find(SCOPE, idempotencyKey)
                .orElseThrow(IdempotencyExceptions.RequestInProgressException::new);

        if (!existing.matches(fingerprint)) {
            log.warn("Idempotency key reused with a different request: scope={}", SCOPE);
            throw new IdempotencyExceptions.KeyConflictException();
        }

        if (!existing.isCompleted() || existing.transactionId() == null) {
            throw new IdempotencyExceptions.RequestInProgressException();
        }

        log.info("Idempotent replay answered from the original transaction: transactionId={}",
                existing.transactionId());

        return transactions.findById(existing.transactionId())
                .orElseThrow(IdempotencyExceptions.RequestInProgressException::new);
    }
}
