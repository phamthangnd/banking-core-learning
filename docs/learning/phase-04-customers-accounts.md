# Phase 04 — Customers and Accounts Notes

Domain-modelling and banking concepts tied to the code written in Phase 04.

## Where a rule belongs

Three rules from this phase, each living somewhere different, and each for a reason:

| Rule | Lives in | Why there |
|---|---|---|
| A balance may not fall below the overdraft | `Account` | It is about one account's own state; nothing else is needed to check it |
| An account may only be activated once KYC is verified | `AccountService` | It spans two aggregates; the account cannot see the customer |
| A customer with open accounts cannot be closed | `CustomerService`, through a port | It is a customer rule that depends on an account fact |

The pattern: a rule goes as close to the data it constrains as it can get, and no closer. Pushing
the KYC check into `Account` would mean the account fetching a customer — a module reaching
across a boundary, which is what boundaries exist to prevent.

## Dependency inversion between modules

`CustomerService` needs to know whether a customer still holds accounts. Calling `AccountService`
directly would make the two modules mutually dependent, and a modular monolith with cycles is
just a monolith.

Instead the customer module **declares** what it needs:

```java
// customer/domain
public interface CustomerAccountsPort {
    boolean hasOpenAccounts(UUID customerId);
}
```

and the account module **implements** it:

```java
// account/infrastructure
@Component
class CustomerAccountsAdapter implements CustomerAccountsPort { … }
```

The dependency arrow now points from `account` to `customer` only. The customer module compiles
without the account module existing, which is the test that matters — and it is also what makes
extracting a service later a mechanical job rather than an archaeology project.

Note the adapter goes to the repository, not to `AccountService`: this is the system checking its
own invariant, so it must not be subject to the caller's `account:read` permission.

## Enums that carry rules

`AccountType` is not a label; it decides whether the account may go negative at all:

```java
CHECKING(true), SAVINGS(false), TERM_DEPOSIT(false);
```

`AccountStatus.canTransact()` answers the one question Phase 05 will ask on every single
operation. Putting it on the enum means there is exactly one definition of "may this account move
money", instead of `status == ACTIVE` scattered across a dozen services and slowly drifting.

Both transition tables are exhaustive `switch` expressions over the enum, so adding a state
without deciding its transitions is a compile error.

## Balance, available balance, overdraft

Three different numbers that are easy to conflate:

| | Meaning |
|---|---|
| `balance` | What is actually on the account, possibly negative |
| `overdraftLimit` | How far below zero it may go, as a **positive** number |
| `availableBalance` | `balance + overdraftLimit` — what may still be withdrawn |

Storing the limit as a positive distance rather than a negative floor keeps the check readable
(`balance >= -limit`) and makes "no overdraft" the natural zero. Returning `availableBalance` in
the API rather than letting clients compute it means the rule has one implementation.

## The database as the last line of defence

The domain refuses to create an illegal balance. The migration refuses too:

```sql
CONSTRAINT ck_accounts_balance_within_overdraft CHECK (balance >= -overdraft_limit)
CONSTRAINT ck_accounts_closed_balance_is_zero   CHECK (status <> 'CLOSED' OR balance = 0)
```

This is not belt and braces for its own sake. Application code has bugs, migrations run
ad-hoc `UPDATE`s, and Phase 05 is about to start moving money concurrently. A check constraint is
the one guarantee that survives all of that — the Phase 04 verification proves it by issuing a
hand-written `UPDATE accounts SET balance = -1` and watching PostgreSQL reject it.

The same reasoning as the unique index on email in Phase 02: only the database can settle an
invariant under concurrency.

## Account numbers

Three decisions, each with a failure mode behind it:

1. **A sequence, not randomness.** `nextval()` never returns the same value twice, even across
   concurrent transactions and even when one rolls back. Random-and-retry works until two
   requests collide at the same instant, which is exactly when you will not be watching.
2. **A Luhn check digit.** Account numbers are typed by humans and dictated over the phone. The
   check digit catches every single-digit error and all adjacent transpositions except `09 ↔ 90`,
   turning a typo into a 404 instead of a transfer to a stranger. The test asserts this by
   mutating every digit of a generated number and requiring each one to be rejected.
3. **No meaning in the identifier.** No branch code, no customer id, no opening date. Encoding a
   fact means the identifier has to change when the fact does — and identifiers cannot change.

## Money at the API boundary

`overdraftLimit` is a `BigDecimal` in the request DTO, not a `double`. Binding it to a `double`
would lose precision *before* any validation could run — the value would already be wrong by the
time the domain saw it. `@Digits(integer = 15, fraction = 4)` matches the column exactly, so a
value that cannot be stored is rejected at the edge rather than at the database.

## Commands, not status writes

`POST /accounts/{id}/activate` rather than `PATCH {"status": "ACTIVE"}`:

- a state machine has rules, and a client writing the next state directly invites skipping them;
- the URL records what happened, which is what an audit trail needs (Phase 07);
- each transition can have its own permission and its own validation.

The same reasoning applies to `POST /customers/{id}/kyc`: a compliance decision is not a field on
an update form.

## Test data and foreign keys

Adding `accounts` broke six existing test classes, all in the same way: they cleaned up with
`customerJpaRepository.deleteAll()`, and the new foreign key refused to delete a customer that
still owned an account.

That failure is the schema working. The fix was not to weaken the constraint to `ON DELETE
CASCADE` — in production, deleting a customer with accounts must fail — but to give the tests a
`DatabaseCleaner` that truncates the whole set in one statement, leaving migration-seeded
reference data alone.

## Checkpoint answers (see `docs/learning/checkpoints.md`)

- **Why BigDecimal for money?** Exact decimal arithmetic with explicit rounding; binary floating
  point cannot represent `0.10`, and the error compounds over a ledger.
- **Why must a closed account not transact?** Its balance has been settled and its history
  finalised; a movement afterwards has no legitimate counterpart and no way to be reconciled.
- **Where do banking invariants belong?** In the type that owns the data, *and* in the database.
  One catches mistakes early and readably; the other is the only thing that holds under
  concurrency and against code that has not been written yet.
