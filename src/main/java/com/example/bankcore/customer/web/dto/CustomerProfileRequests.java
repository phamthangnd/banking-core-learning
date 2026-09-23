package com.example.bankcore.customer.web.dto;

import com.example.bankcore.customer.domain.KycStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request bodies for the customer profile operations added in Phase 04. */
public final class CustomerProfileRequests {

    private CustomerProfileRequests() {
    }

    /**
     * A KYC review outcome.
     *
     * <p>The decision is bound to the enum, so only a value the state machine knows can arrive;
     * whether the move is legal from the current state is still the domain's call.
     */
    public record KycDecision(@NotNull(message = "must not be null") KycStatus status) {
    }

    /** @param fileId file in the file module (Phase 07), or {@code null} to remove the avatar */
    public record Avatar(UUID fileId) {
    }
}
