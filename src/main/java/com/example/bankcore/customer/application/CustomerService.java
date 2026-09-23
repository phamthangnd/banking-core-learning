package com.example.bankcore.customer.application;

import com.example.bankcore.common.pagination.PageRequest;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.customer.config.CustomerProperties;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerEmailAlreadyUsedException;
import com.example.bankcore.customer.domain.CustomerNotFoundException;
import com.example.bankcore.customer.domain.CustomerRepository;
import com.example.bankcore.customer.domain.CustomerRuleViolationException;
import com.example.bankcore.customer.domain.CustomerSearchQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;
import java.util.UUID;

/**
 * Business rules for customers.
 *
 * <p>Every rule lives here rather than in the controller (CLAUDE.md section 2): the controller
 * only translates HTTP to commands and back. That is what lets Phase 08 reuse these rules from a
 * file import and Phase 09 from a message consumer without duplicating a single check.
 *
 * <p>Rules enforced:
 * <ol>
 *   <li>the email address is unique across customers (case-insensitively)</li>
 *   <li>a customer must be at least {@code bankcore.customer.minimum-age-years} old</li>
 *   <li>a birth date in the future is rejected</li>
 *   <li>a closed customer is read-only</li>
 * </ol>
 *
 * <p>Authorization is declared here as well as in the security configuration. The path rules in
 * {@code SecurityConfig} say "you must be authenticated"; the {@code @PreAuthorize} annotations
 * below say which permission each operation needs, and they apply to every caller — a future
 * batch import or message consumer included, not only to requests that arrive through the
 * controller (CLAUDE.md section 4).
 *
 * <p>Transactions are declared here, not in the controller or the repository: the service method
 * is the unit of work. Reads are marked {@code readOnly}, which lets the driver and Hibernate
 * skip dirty checking and tells a future read replica that the work is safe to route there
 * (CLAUDE.md section 3 — balance-changing operations get explicit transaction boundaries).
 *
 * <p>Java note: {@link Clock} is injected instead of calling {@code Instant.now()} directly.
 * "Now" is an input, and an input you can control is an input you can test.
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository repository;
    private final CustomerProperties properties;
    private final Clock clock;

    public CustomerService(CustomerRepository repository, CustomerProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAuthority('customer:write')")
    public Customer create(CustomerCommands.CreateCustomer command) {
        String email = Customer.normalizeEmail(command.email());

        requireEmailAvailable(email, null);
        requireEligibleAge(command.dateOfBirth());

        Instant now = clock.instant();
        Customer customer = Customer.register(UUID.randomUUID(), command.fullName(), email,
                command.phoneNumber(), command.dateOfBirth(), now);

        Customer saved = repository.save(customer);
        // Log the identifier, never the personal data behind it.
        log.info("Customer created: id={}", saved.id());
        return saved;
    }

    @PreAuthorize("hasAuthority('customer:read')")
    public Customer getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    /**
     * One page of customers matching the filters.
     *
     * <p>The requested page size is capped at {@code bankcore.customer.max-page-size}: a client
     * asking for a million rows must not be able to decide how much memory the server allocates.
     */
    @PreAuthorize("hasAuthority('customer:read')")
    public PageResult<Customer> search(CustomerSearchQuery query) {
        PageRequest requested = query.page();
        int cappedSize = Math.min(requested.size(), properties.maxPageSize());

        CustomerSearchQuery capped = new CustomerSearchQuery(
                query.nameFragment(),
                query.email(),
                query.status(),
                new PageRequest(requested.page(), cappedSize, requested.sortProperty(), requested.direction()));

        return repository.search(capped);
    }

    @Transactional
    @PreAuthorize("hasAuthority('customer:write')")
    public Customer update(UUID id, CustomerCommands.UpdateCustomer command) {
        Customer existing = getById(id);

        if (existing.isClosed()) {
            throw new CustomerRuleViolationException("A closed customer cannot be updated");
        }

        String email = Customer.normalizeEmail(command.email());
        requireEmailAvailable(email, id);

        Customer updated = existing.withContactDetails(
                command.fullName(), email, command.phoneNumber(), clock.instant());

        Customer saved = repository.save(updated);
        log.info("Customer updated: id={}", saved.id());
        return saved;
    }

    /**
     * Closes a customer.
     *
     * <p>Customers are never hard-deleted: closing keeps the record for history and for anything
     * that references it. Closing an already closed customer succeeds without changing anything,
     * which makes the operation idempotent (CLAUDE.md section 3).
     */
    @Transactional
    @PreAuthorize("hasAuthority('customer:close')")
    public Customer close(UUID id) {
        Customer existing = getById(id);

        if (existing.isClosed()) {
            return existing;
        }

        Customer closed = repository.save(existing.close(clock.instant()));
        log.info("Customer closed: id={}", closed.id());
        return closed;
    }

    private void requireEmailAvailable(String normalizedEmail, UUID selfId) {
        Optional<Customer> owner = repository.findByEmail(normalizedEmail);
        boolean takenBySomeoneElse = owner
                .filter(customer -> !customer.id().equals(selfId))
                .isPresent();

        if (takenBySomeoneElse) {
            throw new CustomerEmailAlreadyUsedException();
        }
    }

    private void requireEligibleAge(LocalDate dateOfBirth) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());

        if (dateOfBirth.isAfter(today)) {
            throw new CustomerRuleViolationException("Date of birth must not be in the future");
        }

        int age = Period.between(dateOfBirth, today).getYears();
        if (age < properties.minimumAgeYears()) {
            throw new CustomerRuleViolationException(
                    "Customer must be at least %d years old".formatted(properties.minimumAgeYears()));
        }
    }
}
