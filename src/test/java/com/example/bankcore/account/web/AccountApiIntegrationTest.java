package com.example.bankcore.account.web;

import com.example.bankcore.account.infrastructure.persistence.AccountJpaRepository;
import com.example.bankcore.customer.infrastructure.persistence.CustomerJpaRepository;
import com.example.bankcore.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The account API end to end: open, activate, freeze, close, and the rules that guard each step.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "33333333-3333-3333-3333-333333333333",
        authorities = {"customer:read", "customer:write", "customer:close", "customer:kyc",
                "account:read", "account:write", "account:close"})
class AccountApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private CustomerJpaRepository customerJpaRepository;

    @Autowired
    private com.example.bankcore.support.DatabaseCleaner databaseCleaner;

    @BeforeEach
    void clearDatabase() {
        databaseCleaner.clear();
    }

    private String createCustomer(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Account Owner",
                                  "email": "%s",
                                  "phoneNumber": "+84 90 123 4567",
                                  "dateOfBirth": "1990-01-01"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data").path("id").asText();
    }

    private void verifyKyc(String customerId) throws Exception {
        mockMvc.perform(post("/api/v1/customers/{id}/kyc", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"VERIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("VERIFIED"));
    }

    private JsonNode openAccount(String customerId, String type, String currency) throws Exception {
        String response = mockMvc.perform(post("/api/v1/customers/{id}/accounts", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\": \"%s\", \"currency\": \"%s\"}".formatted(type, currency)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data");
    }

    @Test
    void shouldOpenActivateFreezeAndCloseAnAccount() throws Exception {
        String customerId = createCustomer("lifecycle@example.com");
        verifyKyc(customerId);

        JsonNode account = openAccount(customerId, "CHECKING", "VND");
        String id = account.path("id").asText();

        assertThat(account.path("status").asText()).isEqualTo("PENDING");
        assertThat(account.path("canTransact").asBoolean()).isFalse();
        assertThat(account.path("accountNumber").asText()).startsWith("9004");
        assertThat(account.path("balance").decimalValue()).isEqualByComparingTo("0.0000");

        mockMvc.perform(post("/api/v1/accounts/{id}/activate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.canTransact").value(true));

        mockMvc.perform(post("/api/v1/accounts/{id}/freeze", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FROZEN"))
                .andExpect(jsonPath("$.data.canTransact").value(false));

        mockMvc.perform(post("/api/v1/accounts/{id}/unfreeze", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/accounts/{id}/close", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.closedAt").isNotEmpty());

        // Closing twice is idempotent, and the record survives.
        mockMvc.perform(post("/api/v1/accounts/{id}/close", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRefuseActivationBeforeKycIsVerified() throws Exception {
        String customerId = createCustomer("unverified@example.com");
        String id = openAccount(customerId, "CHECKING", "VND").path("id").asText();

        mockMvc.perform(post("/api/v1/accounts/{id}/activate", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_RULE_VIOLATED"))
                .andExpect(jsonPath("$.error.message", org.hamcrest.Matchers.containsString("KYC")));
    }

    @Test
    void shouldLetACustomerOwnSeveralAccountsInDifferentCurrencies() throws Exception {
        String customerId = createCustomer("multi@example.com");
        verifyKyc(customerId);

        openAccount(customerId, "CHECKING", "VND");
        openAccount(customerId, "SAVINGS", "VND");
        openAccount(customerId, "SAVINGS", "USD");

        mockMvc.perform(get("/api/v1/customers/{id}/accounts", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.metadata.totalElements").value(3));

        mockMvc.perform(get("/api/v1/accounts").param("customerId", customerId).param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].currency").value("USD"));
    }

    @Test
    void shouldLookUpAnAccountByItsNumber() throws Exception {
        String customerId = createCustomer("by-number@example.com");
        String accountNumber = openAccount(customerId, "SAVINGS", "VND").path("accountNumber").asText();

        mockMvc.perform(get("/api/v1/accounts/by-number/{number}", accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNumber").value(accountNumber));

        mockMvc.perform(get("/api/v1/accounts/by-number/{number}", "00000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void shouldGrantAnOverdraftOnlyWhereTheTypeAllowsIt() throws Exception {
        String customerId = createCustomer("overdraft@example.com");
        String checking = openAccount(customerId, "CHECKING", "VND").path("id").asText();
        String savings = openAccount(customerId, "SAVINGS", "VND").path("id").asText();

        mockMvc.perform(put("/api/v1/accounts/{id}/overdraft-limit", checking)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overdraftLimit\": 500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overdraftLimit").value(500000.0000))
                .andExpect(jsonPath("$.data.availableBalance").value(500000.0000));

        mockMvc.perform(put("/api/v1/accounts/{id}/overdraft-limit", savings)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overdraftLimit\": 1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_RULE_VIOLATED"));
    }

    @Test
    void shouldRefuseToClosACustomerThatStillHoldsAnAccount() throws Exception {
        String customerId = createCustomer("still-has-accounts@example.com");
        String accountId = openAccount(customerId, "SAVINGS", "VND").path("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/customers/{id}", customerId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CUSTOMER_RULE_VIOLATED"));

        mockMvc.perform(post("/api/v1/accounts/{id}/close", accountId))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/customers/{id}", customerId))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldRejectAnInvalidOpenRequest() throws Exception {
        String customerId = createCustomer("invalid@example.com");

        mockMvc.perform(post("/api/v1/customers/{id}/accounts", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\": null, \"currency\": \"vnd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("accountType"));
    }

    @Test
    void shouldRejectAnUnknownAccountType() throws Exception {
        String customerId = createCustomer("bad-type@example.com");

        mockMvc.perform(post("/api/v1/customers/{id}/accounts", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\": \"CRYPTO_WALLET\", \"currency\": \"VND\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void shouldRecordAndClearAnAvatarReference() throws Exception {
        String customerId = createCustomer("avatar@example.com");
        String fileId = "44444444-4444-4444-4444-444444444444";

        mockMvc.perform(put("/api/v1/customers/{id}/avatar", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\": \"%s\"}".formatted(fileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarFileId").value(fileId));

        mockMvc.perform(put("/api/v1/customers/{id}/avatar", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarFileId").doesNotExist());
    }

    @Test
    void shouldRefuseAnIllegalKycDecision() throws Exception {
        String customerId = createCustomer("kyc-rules@example.com");
        verifyKyc(customerId);

        mockMvc.perform(post("/api/v1/customers/{id}/kyc", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"REJECTED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CUSTOMER_RULE_VIOLATED"));
    }
}
