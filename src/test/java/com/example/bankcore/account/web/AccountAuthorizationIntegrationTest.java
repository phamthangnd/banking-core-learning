package com.example.bankcore.account.web;

import com.example.bankcore.account.infrastructure.persistence.AccountJpaRepository;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may do what with accounts, using real tokens through the whole filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class AccountAuthorizationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private CustomerJpaRepository customerJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private com.example.bankcore.support.DatabaseCleaner databaseCleaner;

    private String customerId;
    private String accountId;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.clear();

        seedCustomerAndAccount();
    }

    @WithMockUser(authorities = {"customer:write", "account:write"})
    private void seedCustomerAndAccount() throws Exception {
        // Seeding runs as a user that holds the write permissions; the assertions below use
        // real tokens for the roles under test.
        String customerResponse = mockMvc.perform(post("/api/v1/customers")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("customer:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "RBAC Owner",
                                  "email": "rbac-owner@example.com",
                                  "phoneNumber": "+84 90 123 4567",
                                  "dateOfBirth": "1990-01-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        customerId = objectMapper.readTree(customerResponse).path("data").path("id").asText();

        String accountResponse = mockMvc.perform(post("/api/v1/customers/{id}/accounts", customerId)
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("account:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\": \"SAVINGS\", \"currency\": \"VND\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        accountId = objectMapper.readTree(accountResponse).path("data").path("id").asText();
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

    @Test
    void shouldRequireAuthenticationForEveryAccountEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/accounts/{id}/activate", accountId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tellerShouldReadAccountsButNotChangeThem() throws Exception {
        String token = tokenFor("teller-accounts", "TELLER");

        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/accounts/{id}/activate", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/accounts/{id}/close", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void officerShouldRunTheWholeAccountLifecycle() throws Exception {
        String token = tokenFor("officer-accounts", "OFFICER");

        mockMvc.perform(post("/api/v1/customers/{id}/kyc", customerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"VERIFIED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/accounts/{id}/activate", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/accounts/{id}/close", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void customerRoleShouldNotReachTheAccountApi() throws Exception {
        String token = tokenFor("self-service-accounts", "CUSTOMER");

        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/customers/{id}/accounts", customerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\": \"SAVINGS\", \"currency\": \"VND\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void kycDecisionShouldNeedItsOwnPermission() throws Exception {
        // A teller may read customers but must not decide whether the bank accepts them.
        String token = tokenFor("teller-kyc", "TELLER");

        mockMvc.perform(post("/api/v1/customers/{id}/kyc", customerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"VERIFIED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }
}
