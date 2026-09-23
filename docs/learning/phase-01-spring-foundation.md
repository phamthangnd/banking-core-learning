# Phase 01 — Spring Boot Foundation Notes

Spring concepts tied to the code written in Phase 01.

## Inversion of Control and dependency injection

`CustomerService` needs a `CustomerRepository`, a `CustomerProperties` and a `Clock`. It never
creates them: it declares them as constructor parameters and Spring supplies them. That is
inversion of control — the framework owns the wiring.

- **Constructor injection**, not `@Autowired` on fields: the dependencies become `final`, the
  object cannot exist half-built, and the class is trivially testable with `new CustomerService(...)`
  (which is exactly what `CustomerServiceTest` does — no Spring context at all).
- A single constructor needs no `@Autowired` annotation at all in modern Spring.
- Field injection would make those tests impossible without reflection, which is the real reason
  to avoid it.

## Beans and stereotypes

A bean is an object whose lifecycle Spring manages. `@Component` and its specialisations mark a
class for component scanning:

| Annotation | Used for | Example |
|---|---|---|
| `@RestController` | HTTP boundary | `CustomerController` |
| `@Service` | application/business logic | `CustomerService` |
| `@Repository` | persistence adapter | `InMemoryCustomerRepository` |
| `@Configuration` + `@Bean` | objects you don't own | `TimeConfig.clock()` |

The stereotypes are not decoration: they document layers, and `@Repository` additionally
translates persistence exceptions once Phase 02 introduces JPA.

Default scope is **singleton** — one instance shared by every request. That is why beans must be
stateless or thread-safe: `InMemoryCustomerRepository` uses a `ConcurrentHashMap` for exactly
this reason.

## Configuration properties

`CustomerProperties` binds `bankcore.customer.*` to a record:

- `@ConfigurationProperties` + a record gives immutable, typed configuration. A typo in a
  property name leaves the default in place; a typo in a *value* fails at startup because
  `@Validated` runs the constraints during binding.
- Compare with `@Value("${...}")`: untyped, scattered, unvalidated, and easy to misspell.
- `@ConfigurationPropertiesScan` on the application class finds the record without a separate
  `@EnableConfigurationProperties`.
- Business rules read the property (`properties.minimumAgeYears()`), so changing the minimum age
  is configuration, not a code change.

## REST and Jackson

- `@RequestBody` deserialises JSON into a DTO; the return value is serialised back. Records work
  directly because the compiler keeps the component names (the build passes `-parameters`).
- `spring.jackson.default-property-inclusion: non_null` keeps null fields out of the JSON, which
  is what lets one envelope serve both success and error responses.
- `write-dates-as-timestamps: false` renders `Instant` and `LocalDate` as ISO-8601 strings
  instead of epoch numbers. Timestamps in an API are read by humans too.
- `ResponseEntity` is used where the status or headers matter (201 + `Location`); plain return
  values are used where 200 is right. `@ResponseStatus(NO_CONTENT)` covers the 204 case.

## Bean Validation vs business rules

Two different questions, deliberately answered in two different places:

| Question | Where | Failure |
|---|---|---|
| Is the request well-formed? | `@Valid` on the DTO | 400 `VALIDATION_FAILED` |
| Is the request allowed by the domain? | `CustomerService` | 409 / 422 with a domain error code |

"Is this a syntactically valid email?" is validation. "Is this email already taken?" is a
business rule that needs the repository. Putting the second one in an annotation would drag
persistence into the DTO layer.

`@Valid` triggers `MethodArgumentNotValidException`, which the handler turns into a sorted list
of `ValidationError` — sorted so the response is deterministic and testable.

## @RestControllerAdvice

`GlobalExceptionHandler` is the single place where a failure becomes an HTTP status.

- Controllers contain no `try`/`catch`, so they stay readable.
- Domain code throws `BusinessException` subclasses carrying an `ErrorCode`; the handler maps the
  code's *category* to a status. The domain therefore never imports `HttpStatus` — a framework
  type has no business being in a business rule.
- The catch-all `@ExceptionHandler(Exception.class)` logs the stack trace and returns an opaque
  500. Internal details (class names, SQL, stack frames) are an information leak, and the
  `traceId` is what connects the client's report to the log entry.
- `@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`.

## Layering

```
web (CustomerController, DTOs)
  -> application (CustomerService, commands)
    -> domain (Customer, CustomerRepository port, business exceptions)
      <- infrastructure (InMemoryCustomerRepository implements the port)
```

- The controller maps DTO to command; the service never sees a web type.
- The repository **interface** lives in `domain`, its implementation in `infrastructure`. The
  dependency arrow points inward, so Phase 02 can swap the in-memory adapter for JPA without
  touching a single business rule.
- The domain model is never returned from a controller — `CustomerResponse` exists so the API
  contract and the internal model can evolve independently.

## Testing layers

| Test | Loads | Proves |
|---|---|---|
| `CustomerServiceTest` | nothing (plain JUnit) | business rules, with a fixed `Clock` |
| `CustomerControllerTest` | `@WebMvcTest` + mocked service | status codes, envelope, error mapping |
| `CustomerApiIntegrationTest` | `@SpringBootTest` | the whole stack end to end |

- `@WebMvcTest` starts only the web slice, so it is fast — but it does **not** pick up arbitrary
  `@Configuration` classes, which is why `SecurityConfig` is imported explicitly. Without it the
  test would run against Spring Security's default chain and every request would return 401.
- `@MockitoBean` (Spring Boot 3.4+) replaces the deprecated `@MockBean`.
- Injecting a fixed `Clock` removes time from the test's inputs: the "exactly 18 years old" case
  is only testable because "today" is pinned.

## Correlation ids

`CorrelationIdFilter` runs first for every request, puts a trace id into SLF4J's `MDC`, echoes it
in `X-Trace-Id` and clears it afterwards.

- The logging pattern includes `%X{traceId}`, so every line of a request carries the same id.
- The `finally` block is not optional: servlet threads are pooled, and a leftover id would
  attach itself to the next, unrelated request.
- The inbound header is sanitised before use — it is untrusted input that ends up in log lines,
  and log forging is a real attack.
- MDC is thread-local and does not follow work to another thread; async propagation is revisited
  in Phase 09 and Phase 11.

## Checkpoint answers (see `docs/learning/checkpoints.md`)

- **What is dependency injection?** The framework supplies a class's collaborators instead of the
  class constructing them, so wiring and policy live outside the business logic.
- **What is a Spring bean?** An object whose creation, configuration and lifecycle Spring manages;
  singleton by default, therefore required to be thread-safe.
- **Why separate controller/service/repository?** Each layer has one reason to change — HTTP
  shape, business rules, storage technology — and the separation is what makes Phase 02's
  database swap a local change.
