package com.example.bankcore.customer.web.dto;

import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerStatus;
import com.example.bankcore.customer.domain.KycStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response body for a customer.
 *
 * <p>A separate type from the domain model on purpose: the API contract must be able to stay
 * stable while the domain model evolves, and internal fields must never leak just because
 * someone added them to the model. From Phase 02 the model is a JPA entity, and entities are
 * never returned from a controller (CLAUDE.md section 2).
 */
public record CustomerResponse(
        UUID id,
        String fullName,
        String email,
        String phoneNumber,
        LocalDate dateOfBirth,
        CustomerStatus status,
        KycStatus kycStatus,
        Instant kycReviewedAt,
        UUID avatarFileId,
        Instant createdAt,
        Instant updatedAt
) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.id(),
                customer.fullName(),
                customer.email(),
                customer.phoneNumber(),
                customer.dateOfBirth(),
                customer.status(),
                customer.kycStatus(),
                customer.kycReviewedAt(),
                customer.avatarFileId(),
                customer.createdAt(),
                customer.updatedAt());
    }
}
