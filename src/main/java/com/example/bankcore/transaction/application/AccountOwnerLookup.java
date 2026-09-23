package com.example.bankcore.transaction.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Finds the user who should be told about activity on an account.
 *
 * <p>A port, because the answer lives outside this module and the link between a customer and a
 * login does not exist yet. The default implementation returns nothing, so events are published
 * with no recipient and the notification consumer skips them — honest about the gap rather than
 * inventing a mapping.
 */
public interface AccountOwnerLookup {

    Optional<UUID> ownerUserIdOf(UUID accountId);
}
