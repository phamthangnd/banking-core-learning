package com.example.bankcore.auth.web;

import com.example.bankcore.auth.application.PasswordResetTokenSender;
import com.example.bankcore.auth.infrastructure.persistence.PasswordResetTokenJpaRepository;
import com.example.bankcore.auth.infrastructure.persistence.RefreshTokenJpaRepository;
import com.example.bankcore.support.PostgresIntegrationTest;
import com.example.bankcore.support.TestUsers;
import com.example.bankcore.user.domain.User;
import com.example.bankcore.user.infrastructure.persistence.UserJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Forgot password, reset password and change password.
 *
 * <p>The reset token is captured through the delivery port. That is the only way to see it: it
 * is stored hashed and is deliberately never logged, so there is no back door — which is exactly
 * the property being asserted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestUsers.class, PasswordFlowIntegrationTest.CapturingSenderConfiguration.class})
class PasswordFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String NEW_PASSWORD = "A-Completely-New-Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private CapturingSender capturingSender;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private PasswordResetTokenJpaRepository resetTokenJpaRepository;

    @BeforeEach
    void clearState() {
        resetTokenJpaRepository.deleteAll();
        refreshTokenJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        capturingSender.clear();
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private String requestReset(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());

        return capturingSender.lastToken();
    }

    @Test
    void shouldResetThePasswordWithAValidToken() throws Exception {
        testUsers.create("reset-me", "CUSTOMER");
        String token = requestReset("reset-me@example.com");

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(token, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        login("reset-me", NEW_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"reset-me\", \"password\": \"%s\"}".formatted(TestUsers.PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldStoreTheResetTokenHashed() throws Exception {
        testUsers.create("hash-check", "CUSTOMER");
        String token = requestReset("hash-check@example.com");

        String storedRows = objectMapper.writeValueAsString(resetTokenJpaRepository.findAll());

        assertThat(token).isNotBlank();
        assertThat(storedRows).doesNotContain(token);
    }

    @Test
    void shouldAcceptAResetTokenOnlyOnce() throws Exception {
        testUsers.create("single-use", "CUSTOMER");
        String token = requestReset("single-use@example.com");

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(token, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"newPassword\": \"Yet-Another-Passw0rd\"}".formatted(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void shouldInvalidateAnEarlierTokenWhenANewOneIsRequested() throws Exception {
        testUsers.create("two-requests", "CUSTOMER");
        String first = requestReset("two-requests@example.com");
        String second = requestReset("two-requests@example.com");

        assertThat(second).isNotEqualTo(first);

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(first, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAnUnknownResetToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"never-issued\", \"newPassword\": \"%s\"}".formatted(NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotRevealWhetherAnEmailIsRegistered() throws Exception {
        testUsers.create("known-user", "CUSTOMER");

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"known-user@example.com\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody-here@example.com\"}"))
                .andExpect(status().isAccepted());

        // Same status for both, and nothing was sent for the unknown address.
        assertThat(capturingSender.tokens()).hasSize(1);
    }

    @Test
    void shouldRevokeEverySessionAfterAPasswordReset() throws Exception {
        testUsers.create("session-killer", "CUSTOMER");

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"session-killer\", \"password\": \"%s\"}"
                                .formatted(TestUsers.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginResponse).path("data").path("refreshToken").asText();

        String token = requestReset("session-killer@example.com");
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(token, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        // A reset is how a user reacts to a compromise; it has to end the attacker's session.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldChangeThePasswordOfTheAuthenticatedUser() throws Exception {
        testUsers.create("changer", "CUSTOMER");
        String accessToken = login("changer", TestUsers.PASSWORD);

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}"
                                .formatted(TestUsers.PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        login("changer", NEW_PASSWORD);
    }

    @Test
    void shouldRefuseAChangeWithoutTheCurrentPassword() throws Exception {
        testUsers.create("wrong-current", "CUSTOMER");
        String accessToken = login("wrong-current", TestUsers.PASSWORD);

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"Not-The-Current-One\", \"newPassword\": \"%s\"}"
                                .formatted(NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldRefuseReusingTheSamePassword() throws Exception {
        testUsers.create("same-again", "CUSTOMER");
        String accessToken = login("same-again", TestUsers.PASSWORD);

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}"
                                .formatted(TestUsers.PASSWORD, TestUsers.PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("WEAK_PASSWORD"));
    }

    @Test
    void shouldRequireAuthenticationToChangeAPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"a\", \"newPassword\": \"b\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Captures the delivered token so the test can redeem it, standing in for Phase 07's email. */
    static class CapturingSender implements PasswordResetTokenSender {

        private final List<String> tokens = new ArrayList<>();

        @Override
        public void send(User user, String rawToken) {
            tokens.add(rawToken);
        }

        String lastToken() {
            return tokens.get(tokens.size() - 1);
        }

        List<String> tokens() {
            return List.copyOf(tokens);
        }

        void clear() {
            tokens.clear();
        }
    }

    @TestConfiguration
    static class CapturingSenderConfiguration {

        @Bean
        @Primary
        CapturingSender capturingSender() {
            return new CapturingSender();
        }
    }
}
