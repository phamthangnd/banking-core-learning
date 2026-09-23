package com.example.bankcore.transaction.application;

import com.example.bankcore.audit.application.AuditService;
import com.example.bankcore.audit.domain.AuditOutcome;
import com.example.bankcore.account.domain.Account;
import com.example.bankcore.account.domain.AccountExceptions;
import com.example.bankcore.account.domain.AccountRepository;
import com.example.bankcore.common.events.application.EventPublisher;
import com.example.bankcore.common.events.domain.DomainEvents;
import com.example.bankcore.common.money.Money;
import com.example.bankcore.common.observability.BankingMetrics;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.transaction.domain.Transaction;
import com.example.bankcore.transaction.domain.TransactionExceptions;
import com.example.bankcore.transaction.domain.TransactionRepository;
import com.example.bankcore.transaction.domain.TransactionSearchQuery;
import com.example.bankcore.transaction.domain.TransactionStatus;
import com.example.bankcore.transaction.domain.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Deposits, withdrawals and transfers.
 *
 * <p>The rule the whole module exists to keep (CLAUDE.md section 3): <b>a balance is never
 * changed without the corresponding business transaction being written in the same database
 * transaction.</b> Both accounts and the transaction row commit together, or nothing does.
 * {@code @Transactional} is what makes "atomic transfer" true; without it a crash between the
 * debit and the credit would destroy money.
 *
 * <p>Each operation follows the same shape:
 * <ol>
 *   <li>load the accounts and check the rules — active, right currency, enough available funds;</li>
 *   <li>on rejection, record a FAILED transaction in its own transaction and throw;</li>
 *   <li>otherwise apply the balance changes and write a POSTED transaction, all in one unit of
 *       work.</li>
 * </ol>
 *
 * <p>What is deliberately <em>not</em> here yet: double-entry ledger entries, idempotency keys
 * and the locking that makes concurrent transfers safe. Those are Phase 06. Optimistic locking
 * on {@code accounts} already prevents a lost update from silently overwriting a balance.
 */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final TransactionReferences references;
    private final TransactionFailureRecorder failureRecorder;
    private final LedgerPosting ledgerPosting;
    private final AuditService audit;
    private final EventPublisher events;
    private final AccountOwnerLookup ownerLookup;
    private final BankingMetrics metrics;
    private final Clock clock;

    public TransactionService(TransactionRepository transactions, AccountRepository accounts,
                              TransactionReferences references,
                              TransactionFailureRecorder failureRecorder,
                              LedgerPosting ledgerPosting, AuditService audit,
                              EventPublisher events, AccountOwnerLookup ownerLookup,
                              BankingMetrics metrics, Clock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.references = references;
        this.failureRecorder = failureRecorder;
        this.ledgerPosting = ledgerPosting;
        this.audit = audit;
        this.events = events;
        this.ownerLookup = ownerLookup;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Money enters the bank and lands on one account. */
    @Transactional
    @PreAuthorize("hasAuthority('transaction:write')")
    public Transaction deposit(TransactionCommands.Deposit command) {
        Instant now = clock.instant();
        Account account = lockAccount(command.accountId());
        Money amount = amountOf(command.amount(), command.currency(), account,
                TransactionType.DEPOSIT, null, account.id(), command.description());

        requireTransactable(account, TransactionType.DEPOSIT, amount, null, account.id(), command.description());

        Account credited = accounts.save(account.credit(amount, now));

        Transaction posted = transactions.save(Transaction.posted(UUID.randomUUID(), references.next(),
                TransactionType.DEPOSIT, amount, null, credited.id(),
                null, credited.balance(), command.description(), now));

        ledgerPosting.post(posted, null, credited.balance(), now);

        log.info("Deposit posted: reference={} accountId={} currency={}",
                posted.reference(), credited.id(), amount.currency());
        audit.recordSuccess("DEPOSIT", "TRANSACTION", posted.id().toString());
        metrics.transactionPosted("DEPOSIT", amount.currency());
        publishPosted(posted, credited.id());
        return posted;
    }

    /** Money leaves one account and the bank. */
    @Transactional
    @PreAuthorize("hasAuthority('transaction:write')")
    public Transaction withdraw(TransactionCommands.Withdraw command) {
        Instant now = clock.instant();
        Account account = lockAccount(command.accountId());
        Money amount = amountOf(command.amount(), command.currency(), account,
                TransactionType.WITHDRAWAL, account.id(), null, command.description());

        requireTransactable(account, TransactionType.WITHDRAWAL, amount, account.id(), null, command.description());

        Account debited = debitOrRecordFailure(account, amount, now,
                TransactionType.WITHDRAWAL, account.id(), null, command.description());

        Transaction posted = transactions.save(Transaction.posted(UUID.randomUUID(), references.next(),
                TransactionType.WITHDRAWAL, amount, debited.id(), null,
                debited.balance(), null, command.description(), now));

        ledgerPosting.post(posted, debited.balance(), null, now);

        log.info("Withdrawal posted: reference={} accountId={} currency={}",
                posted.reference(), debited.id(), amount.currency());
        audit.recordSuccess("WITHDRAWAL", "TRANSACTION", posted.id().toString());
        metrics.transactionPosted("WITHDRAWAL", amount.currency());
        publishPosted(posted, debited.id());
        return posted;
    }

    /**
     * Moves money between two accounts of this bank.
     *
     * <p>Atomic by construction: the debit, the credit and the transaction row are one unit of
     * work. If the credit fails for any reason, the debit is rolled back with it — the money
     * never sits in between.
     */
    @Transactional
    @PreAuthorize("hasAuthority('transaction:write')")
    public Transaction transfer(TransactionCommands.Transfer command) {
        Instant now = clock.instant();

        if (command.sourceAccountId().equals(command.targetAccountId())) {
            // Not recorded as a FAILED transaction: no valid transaction row can represent a
            // transfer to itself, so there is nothing to write. This is a malformed request
            // rather than a refused financial operation, and the DTO rejects it first.
            throw new TransactionExceptions.TransactionRejectedException(
                    "Source and target accounts must differ");
        }

        // Locks are taken in a fixed order (by id), not in the order the caller named the
        // accounts. Two simultaneous transfers A->B and B->A would otherwise each hold the lock
        // the other needs, and deadlock; ordering makes that impossible.
        Account source;
        Account target;
        if (command.sourceAccountId().compareTo(command.targetAccountId()) < 0) {
            source = lockAccount(command.sourceAccountId());
            target = lockAccount(command.targetAccountId());
        } else {
            target = lockAccount(command.targetAccountId());
            source = lockAccount(command.sourceAccountId());
        }

        Money amount = amountOf(command.amount(), command.currency(), source, TransactionType.TRANSFER,
                source.id(), target.id(), command.description());

        requireTransactable(source, TransactionType.TRANSFER, amount, source.id(), target.id(), command.description());
        requireTransactable(target, TransactionType.TRANSFER, amount, source.id(), target.id(), command.description());

        // No foreign exchange in this phase: converting would need a rate, a source for it and a
        // rounding policy, and getting any of those silently wrong loses money.
        if (!source.currency().equals(target.currency())) {
            throw rejected(TransactionType.TRANSFER, amount, source.id(), target.id(), command.description(),
                    "Cross-currency transfers are not supported");
        }

        Account debited = debitOrRecordFailure(source, amount, now, TransactionType.TRANSFER,
                source.id(), target.id(), command.description());
        Account credited = accounts.save(target.credit(amount, now));

        Transaction posted = transactions.save(Transaction.posted(UUID.randomUUID(), references.next(),
                TransactionType.TRANSFER, amount, debited.id(), credited.id(),
                debited.balance(), credited.balance(), command.description(), now));

        ledgerPosting.post(posted, debited.balance(), credited.balance(), now);

        log.info("Transfer posted: reference={} sourceId={} targetId={} currency={}",
                posted.reference(), debited.id(), credited.id(), amount.currency());
        audit.recordSuccess("TRANSFER", "TRANSACTION", posted.id().toString());
        metrics.transactionPosted("TRANSFER", amount.currency());
        publishPosted(posted, credited.id());
        return posted;
    }

    /**
     * Reverses a posted transaction by posting its mirror image.
     *
     * <p>The original is never edited — it is marked REVERSED and a new transaction moves the
     * money back. That is what "corrections use compensating transactions" means (CLAUDE.md
     * section 3): the history keeps both the mistake and the fix, and a statement can be
     * reconciled afterwards.
     *
     * <p>The reversal can itself fail — the money may already have been spent — and then nothing
     * happens at all, which is the honest outcome.
     */
    @Transactional
    @PreAuthorize("hasAuthority('transaction:write')")
    public Transaction reverse(UUID transactionId, String reason) {
        Transaction original = transactions.findById(transactionId)
                .orElseThrow(() -> new TransactionExceptions.TransactionNotFoundException(transactionId));

        if (!original.isPosted()) {
            throw new TransactionExceptions.TransactionRejectedException(
                    "Only a posted transaction can be reversed; this one is " + original.status());
        }

        Instant now = clock.instant();
        Money amount = original.amount();
        String description = "Reversal of %s%s".formatted(original.reference(),
                reason == null || reason.isBlank() ? "" : ": " + reason);

        // The mirror image: whatever was debited is credited and the other way round.
        Transaction compensating = switch (original.type()) {
            case DEPOSIT -> withdrawFor(original.targetAccountId(), amount, description);
            case WITHDRAWAL -> depositFor(original.sourceAccountId(), amount, description);
            case TRANSFER -> transferFor(original.targetAccountId(), original.sourceAccountId(),
                    amount, description);
        };

        transactions.save(original.withStatus(TransactionStatus.REVERSED, now));
        log.info("Transaction reversed: original={} compensating={}",
                original.reference(), compensating.reference());
        audit.record("TRANSACTION_REVERSED", "TRANSACTION", original.id().toString(),
                AuditOutcome.SUCCESS, "compensated by " + compensating.reference());

        return compensating;
    }

    // These three call public methods on this same bean. Spring's proxy is bypassed by a
    // self-invocation, so the nested @PreAuthorize and @Transactional do not re-apply — which is
    // what is wanted here: the caller has already been authorized for the reversal, and the
    // compensating movement must run inside the reversal's own transaction, not a new one.

    private Transaction depositFor(UUID accountId, Money amount, String description) {
        return deposit(new TransactionCommands.Deposit(accountId, amount.amount(), amount.currency(), description));
    }

    private Transaction withdrawFor(UUID accountId, Money amount, String description) {
        return withdraw(new TransactionCommands.Withdraw(accountId, amount.amount(), amount.currency(), description));
    }

    private Transaction transferFor(UUID sourceId, UUID targetId, Money amount, String description) {
        return transfer(new TransactionCommands.Transfer(sourceId, targetId, amount.amount(),
                amount.currency(), description));
    }

    @PreAuthorize("hasAuthority('transaction:read')")
    public Transaction getById(UUID id) {
        return transactions.findById(id)
                .orElseThrow(() -> new TransactionExceptions.TransactionNotFoundException(id));
    }

    @PreAuthorize("hasAuthority('transaction:read')")
    public Transaction getByReference(String reference) {
        return transactions.findByReference(reference)
                .orElseThrow(() -> new TransactionExceptions.TransactionNotFoundException(reference));
    }

    /** Transaction history, filtered and paginated. */
    @PreAuthorize("hasAuthority('transaction:read')")
    public PageResult<Transaction> search(TransactionSearchQuery query) {
        return transactions.search(query);
    }

    /**
     * Records a {@code TransactionPosted} event in the outbox.
     *
     * <p>Written inside this transaction, so the event and the money movement commit together. A
     * consumer turns it into a notification; doing that inline would let a slow notification hold
     * up a transfer.
     *
     * <p>The payload carries identifiers only — no amount and no balance. A consumer that needs
     * them asks the API, with its own authorization (CLAUDE.md section 4).
     */
    private void publishPosted(Transaction posted, UUID notifiedAccountId) {
        events.publish(DomainEvents.TOPIC_TRANSACTIONS, posted.id().toString(),
                DomainEvents.TRANSACTION_POSTED, new TransactionPostedEvent(
                        posted.id(), posted.reference(), posted.type().name(), posted.currency(),
                        notifiedAccountId, ownerLookup.ownerUserIdOf(notifiedAccountId).orElse(null)));
    }

    /**
     * @param ownerUserId the user to notify, or {@code null} when the account has no linked user
     */
    public record TransactionPostedEvent(UUID transactionId, String reference, String type,
                                         String currency, UUID accountId, UUID ownerUserId) {
    }

    private Account requireAccount(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountExceptions.AccountNotFoundException(id));
    }

    /**
     * Loads an account with a row lock held until this transaction ends.
     *
     * <p>Every balance change goes through here. Two concurrent movements on the same account
     * are serialised by the database instead of racing, which is what makes the "no lost update"
     * guarantee real rather than probabilistic.
     */
    private Account lockAccount(UUID id) {
        return accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountExceptions.AccountNotFoundException(id));
    }

    private Money amountOf(BigDecimal amount, String currency, Account account, TransactionType type,
                           UUID sourceId, UUID targetId, String description) {
        if (amount == null || amount.signum() <= 0) {
            // The recorded amount has to be a legal Money, and a transaction row cannot hold a
            // non-positive one; the reason field is what says the request asked for it.
            Money recordable = new Money(
                    amount == null || amount.signum() == 0 ? BigDecimal.ONE : amount.abs(),
                    account.currency());
            throw rejected(type, recordable, sourceId, targetId, description, "Amount must be positive");
        }

        String resolved = currency == null || currency.isBlank() ? account.currency() : currency;
        Money money = new Money(amount, resolved);

        if (!resolved.equals(account.currency())) {
            throw rejected(type, money, sourceId, targetId, description,
                    "Amount is in %s but the account is in %s".formatted(resolved, account.currency()));
        }

        return money;
    }

    private void requireTransactable(Account account, TransactionType type, Money amount,
                                     UUID sourceId, UUID targetId, String description) {
        if (!account.canTransact()) {
            throw rejected(type, amount, sourceId, targetId, description,
                    "Account %s is %s and cannot transact".formatted(account.accountNumber(), account.status()));
        }
    }

    /**
     * Debits an account, turning a refusal into a recorded FAILED transaction.
     *
     * <p>The available-funds rule lives on the {@code Account}; this only translates its refusal
     * into the module's own failure, so the history shows the attempt.
     */
    private Account debitOrRecordFailure(Account account, Money amount, Instant now, TransactionType type,
                                         UUID sourceId, UUID targetId, String description) {
        try {
            return accounts.save(account.debit(amount, now));
        } catch (AccountExceptions.AccountRuleViolationException ex) {
            throw rejected(type, amount, sourceId, targetId, description, ex.getMessage());
        }
    }

    private TransactionExceptions.TransactionRejectedException rejected(
            TransactionType type, Money amount, UUID sourceId, UUID targetId,
            String description, String reason) {
        failureRecorder.record(type, amount, sourceId, targetId, description, reason);
        // The audit entry records the attempt and why it was refused, never the amount.
        audit.record(type.name(), "TRANSACTION", null, AuditOutcome.FAILURE, reason);
        metrics.transactionRejected(type.name(), reason);
        return new TransactionExceptions.TransactionRejectedException(reason);
    }

}
