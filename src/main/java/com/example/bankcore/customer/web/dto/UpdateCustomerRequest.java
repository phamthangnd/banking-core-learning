package com.example.bankcore.customer.web.dto;

import com.example.bankcore.customer.application.CustomerCommands;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code PUT /api/v1/customers/{id}}.
 *
 * <p>Only contact details can change. Identity and date of birth are fixed after registration:
 * changing them would rewrite the basis of the identity checks already performed.
 */
public record UpdateCustomerRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 150, message = "must be at most 150 characters")
        String fullName,

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email address")
        @Size(max = 255, message = "must be at most 255 characters")
        String email,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "\\+?[0-9 .-]{6,20}", message = "must be a valid phone number")
        String phoneNumber
) {

    public CustomerCommands.UpdateCustomer toCommand() {
        return new CustomerCommands.UpdateCustomer(fullName, email, phoneNumber);
    }
}
