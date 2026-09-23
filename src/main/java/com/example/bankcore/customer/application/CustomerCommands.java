package com.example.bankcore.customer.application;

import java.time.LocalDate;

/**
 * Input models of the customer service.
 *
 * <p>The service takes commands, not web DTOs: the application layer must not depend on the
 * shape of an HTTP request, and a second entry point (a batch import in Phase 08, a message
 * consumer in Phase 09) can reuse exactly the same business rules.
 */
public final class CustomerCommands {

    private CustomerCommands() {
    }

    public record CreateCustomer(String fullName, String email, String phoneNumber, LocalDate dateOfBirth) {
    }

    public record UpdateCustomer(String fullName, String email, String phoneNumber) {
    }
}
