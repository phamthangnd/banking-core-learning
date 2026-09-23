package com.example.bankcore.auth.web;

import com.example.bankcore.auth.infrastructure.persistence.RefreshTokenJpaRepository;
import com.example.bankcore.support.PostgresIntegrationTest;
import com.example.bankcore.support.TestUsers;
import com.example.bankcore.user.domain.UserRepository;
import com.example.bankcore.user.domain.UserStatus;
import com.example.bankcore.user.infrastructure.persistence.UserJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The authentication flows end to end: register, log in, use a token, refresh, log out.
 *
 * <p>Everything runs through the real filter chain against a real database — no stubbed security
 * context, because the point is to prove that the chain, the claims and the token store agree.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class AuthFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private UserRepository users;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private com.example.bankcore.support.DatabaseCleaner databaseCleaner;

    @BeforeEach
    void clearDatabase() {
        databaseCleaner.clear();
    }

    private static String registerBody(String username, String password) {
        return """
                {
                  "username": "%s",
                  "email": "%s@example.com",
                  "password": "%s",
                  "fullName": "Test User"
                }
                """.formatted(username, username, password);
    }

    private static String loginBody(String username, String password) {
        return """
                {"username": "%s", "password": "%s"}
                """.formatted(username, password);
    }

    private JsonNode login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data");
    }

    @Nested
    class Registration {

        @Test
        void shouldRegisterAndNeverReturnThePassword() throws Exception {
            String response = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("alice", TestUsers.PASSWORD)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.username").value("alice"))
                    .andExpect(jsonPath("$.data.roles[0]").value("CUSTOMER"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(response).doesNotContain(TestUsers.PASSWORD);
            assertThat(response).doesNotContain("passwordHash");
        }

        @Test
        void shouldStoreOnlyAHashedPassword() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("bob", TestUsers.PASSWORD)))
                    .andExpect(status().isCreated());

            var stored = users.findByUsername("bob").orElseThrow();

            assertThat(stored.passwordHash()).doesNotContain(TestUsers.PASSWORD);
            assertThat(stored.passwordHash()).startsWith("{bcrypt}$2");
        }

        @Test
        void shouldNormalizeTheUsername() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("MixedCase", TestUsers.PASSWORD)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.username").value("mixedcase"));
        }

        @Test
        void shouldRejectADuplicateWithoutRevealingWhichFieldClashed() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("carol", TestUsers.PASSWORD)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("carol", TestUsers.PASSWORD)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("USER_ALREADY_EXISTS"))
                    .andExpect(jsonPath("$.error.message").value("Username or email is already registered"));
        }

        @Test
        void shouldRejectAWeakPassword() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("dave", "short")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("WEAK_PASSWORD"));
        }
    }

    @Nested
    class Login {

        @Test
        void shouldIssueATokenPair() throws Exception {
            testUsers.create("teller1", "TELLER");

            JsonNode tokens = login("teller1", TestUsers.PASSWORD);

            assertThat(tokens.path("accessToken").asText()).isNotBlank();
            assertThat(tokens.path("tokenType").asText()).isEqualTo("Bearer");
            assertThat(tokens.path("expiresIn").asLong()).isPositive();
            assertThat(tokens.path("refreshToken").asText()).isNotBlank();
        }

        @Test
        void shouldStoreTheRefreshTokenHashedAndNotTheTokenItself() throws Exception {
            testUsers.create("teller2", "TELLER");
            String refreshToken = login("teller2", TestUsers.PASSWORD).path("refreshToken").asText();

            var stored = refreshTokenJpaRepository.findAll();

            assertThat(stored).hasSize(1);
            assertThat(objectMapper.writeValueAsString(stored)).doesNotContain(refreshToken);
        }

        @Test
        void shouldGiveTheSameErrorForUnknownUserAndWrongPassword() throws Exception {
            testUsers.create("teller3", "TELLER");

            String wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("teller3", "Definitely-Wrong-Passw0rd")))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            String unknownUser = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("nobody", "Definitely-Wrong-Passw0rd")))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            // Identical error code and message: the endpoint is not a user-enumeration oracle.
            assertThat(objectMapper.readTree(wrongPassword).path("error").path("code").asText())
                    .isEqualTo(objectMapper.readTree(unknownUser).path("error").path("code").asText())
                    .isEqualTo("INVALID_CREDENTIALS");
            assertThat(objectMapper.readTree(wrongPassword).path("error").path("message").asText())
                    .isEqualTo(objectMapper.readTree(unknownUser).path("error").path("message").asText());
        }

        @Test
        void shouldLockTheAccountAfterRepeatedFailures() throws Exception {
            testUsers.create("teller4", "TELLER");

            // The test profile configures a lockout threshold of 3.
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody("teller4", "Wrong-Passw0rd-Here")))
                        .andExpect(status().isUnauthorized());
            }

            assertThat(users.findByUsername("teller4").orElseThrow().status())
                    .isEqualTo(UserStatus.LOCKED);

            // Even the correct password is refused while the lock holds.
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("teller4", TestUsers.PASSWORD)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_ACTIVE"));
        }

        @Test
        void shouldClearTheFailureCounterAfterASuccessfulLogin() throws Exception {
            testUsers.create("teller5", "TELLER");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("teller5", "Wrong-Passw0rd-Here")))
                    .andExpect(status().isUnauthorized());

            login("teller5", TestUsers.PASSWORD);

            assertThat(users.findByUsername("teller5").orElseThrow().failedLoginAttempts()).isZero();
        }
    }

    @Nested
    class ProtectedEndpoints {

        @Test
        void shouldRejectARequestWithoutAToken() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
        }

        @Test
        void shouldRejectAGarbageToken() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldRejectATokenSignedWithAnotherKey() throws Exception {
            // Header and payload of a well-formed HS256 JWT, signed with a different secret.
            String forged = "eyJhbGciOiJIUzI1NiJ9"
                    + ".eyJpc3MiOiJiYW5rY29yZSIsInN1YiI6IjExMTExMTExLTExMTEtMTExMS0xMTExLTExMTExMTExMTExMSJ9"
                    + ".Zm9yZ2VkLXNpZ25hdHVyZS12YWx1ZS10aGF0LWlzLW5vdC12YWxpZA";

            mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + forged))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldAcceptAValidToken() throws Exception {
            testUsers.create("teller6", "TELLER");
            String accessToken = login("teller6", TestUsers.PASSWORD).path("accessToken").asText();

            mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value("teller6"))
                    .andExpect(jsonPath("$.data.roles[0]").value("TELLER"))
                    .andExpect(jsonPath("$.data.permissions").isArray());
        }
    }

    @Nested
    class RefreshLifecycle {

        @Test
        void shouldRotateTheRefreshTokenOnEveryUse() throws Exception {
            testUsers.create("teller7", "TELLER");
            String first = login("teller7", TestUsers.PASSWORD).path("refreshToken").asText();

            String response = mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"%s\"}".formatted(first)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            String second = objectMapper.readTree(response).path("data").path("refreshToken").asText();

            assertThat(second).isNotEqualTo(first);
        }

        @Test
        void shouldRefuseAnAlreadyRotatedTokenAndRevokeEverySession() throws Exception {
            testUsers.create("teller8", "TELLER");
            String first = login("teller8", TestUsers.PASSWORD).path("refreshToken").asText();

            String response = mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"%s\"}".formatted(first)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String second = objectMapper.readTree(response).path("data").path("refreshToken").asText();

            // Replaying the first token is the signature of a stolen token.
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"%s\"}".formatted(first)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

            // ...and the response to it is to end every session of that user, including the
            // legitimate one that now holds the successor token.
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"%s\"}".formatted(second)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldRefuseAnUnknownRefreshToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"this-token-was-never-issued\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        }

        @Test
        void shouldInvalidateTheRefreshTokenOnLogout() throws Exception {
            testUsers.create("teller9", "TELLER");
            String refreshToken = login("teller9", TestUsers.PASSWORD).path("refreshToken").asText();

            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldTreatLogoutOfAnUnknownTokenAsSuccess() throws Exception {
            // Answering differently would let a caller probe which tokens exist.
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"never-issued-token\"}"))
                    .andExpect(status().isNoContent());
        }
    }
}
