# Phase 00 — Java Comeback Notes

Java notes tied to the code written in Phase 00. Each section names the file that demonstrates it.

## Records — `common/money/Money.java`, `transaction/domain/TransactionRecord.java`

A `record` is a transparent carrier for immutable data. The compiler generates the constructor,
accessors, `equals`, `hashCode` and `toString`.

- Old Java: a class with private final fields, a constructor, getters and hand-written
  `equals`/`hashCode` — dozens of lines that could silently go out of sync with the fields.
- The **compact constructor** (`public Money { ... }`) runs before the fields are assigned and is
  the single place to validate invariants. There is no other way to build the value, so the
  invariant cannot be bypassed.
- Production mistake: `record` accessors are `amount()`, not `getAmount()`. Jackson handles this,
  but older reflection-based libraries may not.
- Production mistake: a record is only as immutable as its components. A record holding a
  `List` is mutable through that list — store `List.copyOf(...)` instead.

## Enums with behaviour — `transaction/domain/TransactionStatus.java`

An enum is a fixed set of instances, and it can carry logic.

- The transition table is a `switch` expression over `this`. Because the switch is exhaustive over
  an enum, adding a new status without deciding its transitions is a **compile error** — the
  rule cannot be forgotten.
- `EnumSet` is a bitmask over the constants: far smaller and faster than `HashSet` for enums.
- `allowedNextStates()` returns a fresh set every call, so no caller can corrupt the table.
  A shared `static final EnumSet` would be mutable and leak.

## BigDecimal — `common/money/Money.java`

`double` is binary floating point; 0.1 has no exact binary representation. `0.1 + 0.2 != 0.3` is
not a bug in the language, it is what binary fractions do — and it is why money is never a
`double` or `float`.

- Build from strings: `new BigDecimal("0.10")` is exact; `BigDecimal.valueOf(0.10)` starts from a
  `double` that already lost precision.
- `equals` compares **value and scale**: `10.0` is not `equals` to `10.00`. Comparing amounts must
  use `compareTo`, which is why `Money` implements `Comparable`.
- Multiplication grows the scale, so it must be followed by an explicit rounding decision.
  `HALF_EVEN` (banker's rounding) is used here: it does not systematically favour one party.
- Division without a scale and rounding mode throws `ArithmeticException` on a non-terminating
  result. That is deliberate — the JDK refuses to guess.

## java.time — `transaction/domain/TransactionRecord.java`

- `Instant` is a point on the UTC timeline: the right type for "when did this happen".
- `LocalDateTime` has no zone and therefore no instant — it is a wall-clock reading, not a moment.
- Financial events are stored in UTC and converted to a local zone only at the edge.
- The legacy `java.util.Date` is mutable and zone-less; it has no place in new code.

## Collections and streams — `transaction/domain/TransactionAnalytics.java`

A stream is a pipeline: a source, lazy intermediate operations, and exactly one terminal
operation. Nothing runs until the terminal operation runs, and the source is never modified.

- `Collectors.toMap(key, value, mergeFunction, mapSupplier)` expresses "group and combine" without
  inventing a fake identity value; the merge function is where the domain rule lives
  (here: `Money::add`, which itself refuses to mix currencies).
- `groupingBy(..., counting())` omits keys with no elements — `countByStatus` fills them in so the
  caller always sees every status.
- `.toList()` (Java 16+) returns an **unmodifiable** list. `Collectors.toList()` does not
  guarantee that.
- Ranges use `[from, to)` — inclusive start, exclusive end — so adjacent periods tile without
  double-counting a transaction that sits exactly on the boundary.

## Optional — `TransactionAnalytics.largestPosted`, `InMemoryBalanceStore.balanceOf`

`Optional` models "there may be no answer" **in a return type**.

- Right: returning `Optional<T>` from a lookup or a `max`/`findFirst`.
- Wrong: `Optional` fields, `Optional` method parameters, or `Optional` in a JPA entity.
- `orElseThrow()` is the honest way to unwrap when absence is a bug; `get()` is the same thing
  with a worse name.

## Exceptions — `CurrencyMismatchException`, `IllegalTransactionTransitionException`, `InsufficientFundsException`

- Mixing currencies or making an illegal state transition is a programming error: unchecked
  exceptions (`IllegalArgumentException` / `IllegalStateException` subclasses) are right.
- Insufficient funds is a business outcome the caller must handle; it is still unchecked here, but
  it is explicit, named, and carries the account and amounts.
- A failed financial operation is **never** swallowed: no empty `catch`, no "return false and hope".
- Never put secrets, tokens or full account data into an exception message — messages end up in
  logs.

## Concurrency — `common/concurrency/InMemoryBalanceStore.java`

`balance = balance + amount` is three operations: read, modify, write. Two threads interleaving
those steps lose one update — the classic lost-update bug, and on a balance it means money
appearing or vanishing.

- `ConcurrentHashMap.compute(key, fn)` performs the whole read-modify-write atomically **for that
  key**. That is what makes the concurrent-credit test deterministic.
- It gives no atomicity **across keys**. A transfer touches two accounts, so it needs a real
  transaction boundary — Phase 06.
- The remapping function runs while the map holds a lock on that bin: keep it short, side-effect
  free, and never touch the same map from inside it.
- A concurrency test needs a `CountDownLatch` to release all threads at once; without it the
  threads run one after another and the race never happens.
- `ExecutorService` is `AutoCloseable` since Java 19, so try-with-resources shuts the pool down and
  waits for the tasks.

## Gradle

- The **wrapper** (`./gradlew`) pins the Gradle version for every machine and for CI.
- A **toolchain** (`languageVersion = 21`) pins the JDK that compiles and runs the code,
  independently of the JDK that launched Gradle.
- `implementation` vs `runtimeOnly`: `implementation` is needed to compile; `runtimeOnly` (the
  PostgreSQL driver) is only needed when running, and keeping it off the compile classpath stops
  code from accidentally importing it.

## Checkpoint answers (see `docs/learning/checkpoints.md`)

- **Why records?** Immutable data carriers with validation in one place and no boilerplate to
  drift out of sync.
- **When Optional?** Return types that may legitimately have no value. Never fields or parameters.
- **List/Set/Map?** Ordered sequence with duplicates / unique elements / key-to-value lookup.
- **What is a stream?** A lazy pipeline over a source, ending in one terminal operation, that does
  not modify the source.
- **Why BigDecimal for money?** Binary floating point cannot represent decimal fractions exactly;
  rounding must be an explicit decision, not an accident.
- **Two threads modifying the same object?** Without an atomic read-modify-write, one update
  overwrites the other and is lost.
