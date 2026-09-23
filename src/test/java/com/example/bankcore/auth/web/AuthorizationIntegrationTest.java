package com.example.bankcore.auth.web;

import com.example.bankcore.auth.infrastructure.persistence.RefreshTokenJpaRepository;
import com.example.bankcore.customer.infrastructure.persistence.CustomerJpaRepository;
import com.example.bankcore.support.PostgresIntegrationTest;
import com.example.bankcore.support.TestUsers;
import com.example.bankcore.user.infrastructure.persistence.UserJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-based access control, exercised with real tokens through the whole chain.
 *
 * <p>The roles come from the seed migration: TELLER reads customers, OFFICER also writes and
 * closes them, CUSTOMER may do neither. Each case asserts both directions — what a role can do
 * and what it must not — because an authorization test that only checks the happy path would
 * pass just as well with every check removed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class AuthorizationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private CustomerJpaRepository customerJpaRepository;

    @BeforeEach
    void clearDatabase() {
        customerJpaRepository.deleteAll();
        refreshTokenJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
    }

    private String tokenFor(String username, String role) throws Exception {
        testUsers.create(username, role);

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}"
                                .formatted(username, TestUsers.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private static String customerBody(String email) {
        return """
                {
                  "fullName": "Alice Nguyen",
                  "email": "%s",
                  "phoneNumber": "+84 90 123 4567",
                  "dateOfBirth": "1990-01-01"
                }
                """.formatted(email);
    }

    @Test
    void tellerShouldReadCustomersButNotCreateThem() throws Exception {
        String token = tokenFor("teller-rbac", "TELLER");

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBody("rbac1@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void officerShouldCreateReadAndCloseCustomers() throws Exception {
        String token = tokenFor("officer-rbac", "OFFICER");

        String created = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBody("rbac2@example.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).path("data").path("id").asText();

        mockMvc.perform(get("/api/v1/customers/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/customers/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void customerRoleShouldNotReachTheCustomerAdministrationApi() throws Exception {
        String token = tokenFor("self-service", "CUSTOMER");

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void anyAuthenticatedUserShouldReachTheirOwnProfile() throws Exception {
        String token = tokenFor("profile-owner", "CUSTOMER");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("profile-owner"));
    }

    @Test
    void adminShouldHoldEveryPermission() throws Exception {
        String token = tokenFor("admin-rbac", "ADMIN");

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBody("rbac3@example.com")))
                .andExpect(status().isCreated());
    }

    @Test
    void aTokenShouldCarryTheRolesPermissionsNotJustTheRoleName() throws Exception {
        String token = tokenFor("claims-check", "TELLER");

        // The permission, not the role, is what the service checks — so it has to be in the token.
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.permissions", org.hamcrest.Matchers.hasItem("customer:read")));
    }
}
