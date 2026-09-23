package com.example.bankcore.auth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenHasherTest {

    @Test
    void shouldProduceAStableHexDigest() {
        String hash = TokenHasher.hash("a-refresh-token");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(TokenHasher.hash("a-refresh-token")).isEqualTo(hash);
    }

    @Test
    void shouldProduceDifferentDigestsForDifferentTokens() {
        assertThat(TokenHasher.hash("token-a")).isNotEqualTo(TokenHasher.hash("token-b"));
    }

    @Test
    void shouldNotContainTheRawToken() {
        // The stored value must not let anyone reconstruct the token it came from.
        assertThat(TokenHasher.hash("secret-token-value")).doesNotContain("secret-token-value");
    }

    @Test
    void shouldRejectBlankInput() {
        assertThatThrownBy(() -> TokenHasher.hash(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TokenHasher.hash(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
