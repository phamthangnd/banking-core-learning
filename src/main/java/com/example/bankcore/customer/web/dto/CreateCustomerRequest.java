package com.example.bankcore.customer.web.dto;

import com.example.bankcore.customer.application.CustomerCommands;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body of {@code POST /api/v1/customers}.
 *
 * <p>Bean Validation runs at the boundary and answers one question only: "is this request
 * well-formed?". Whether the customer is old enough or the email is already taken are business
 * rules, and they live in the service — the two layers of checking are deliberate
 * (CLAUDE.md section 4: validate at the boundary, authorize and decide in the service).
 */
public record CreateCustomerRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 150, message = "must be at most 150 characters")
        String fullName,

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email address")
        @Size(max = 255, message = "must be at most 255 characters")
        String email,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "\\+?[0-9 .-]{6,20}", message = "must be a valid phone number")
        String phoneNumber,

        @NotNull(message = "must not be null")
        @Past(message = "must be in the past")
        LocalDate dateOfBirth
) {

    public CustomerCommands.CreateCustomer toCommand() {
        return new CustomerCommands.CreateCustomer(fullName, email, phoneNumber, dateOfBirth);
    }
}
