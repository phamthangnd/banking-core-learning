package com.example.bankcore.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static RefreshToken token(Instant expiresAt) {
        return RefreshToken.issue(UUID.randomUUID(), UUID.randomUUID(), "hash", NOW, expiresAt);
    }

    @Test
    void shouldBeUsableWhileFreshAndNotRevoked() {
        assertThat(token(NOW.plusSeconds(60)).isUsable(CLOCK)).isTrue();
    }

    @Test
    void shouldNotBeUsableOnceExpired() {
        assertThat(token(NOW).isUsable(CLOCK)).isFalse();
        assertThat(token(NOW.minusSeconds(1)).isExpired(CLOCK)).isTrue();
    }

    @Test
    void shouldNotBeUsableOnceRevoked() {
        RefreshToken revoked = token(NOW.plusSeconds(60)).revoke(NOW);

        assertThat(revoked.isRevoked()).isTrue();
        assertThat(revoked.isUsable(CLOCK)).isFalse();
    }

    @Test
    void shouldKeepTheFirstRevocationTimestamp() {
        RefreshToken revoked = token(NOW.plusSeconds(60)).revoke(NOW);

        assertThat(revoked.revoke(NOW.plusSeconds(10))).isSameAs(revoked);
    }

    @Test
    void shouldRecordTheRotationChain() {
        UUID successor = UUID.randomUUID();
        RefreshToken rotated = token(NOW.plusSeconds(60)).rotateInto(successor, NOW);

        // Rotation both revokes the old token and remembers what replaced it, which is what
        // makes a later presentation of the same token detectable as reuse.
        assertThat(rotated.isRevoked()).isTrue();
        assertThat(rotated.replacedBy()).isEqualTo(successor);
        assertThat(rotated.isUsable(CLOCK)).isFalse();
    }
}
