package com.example.bankcore.customer.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack over real HTTP semantics and a real PostgreSQL: controller, validation, service,
 * JPA adapter, Flyway-migrated schema and its constraints.
 *
 * <p>The contract asserted here is the same one Phase 01 asserted against the in-memory
 * repository — swapping the adapter did not change the API, which is the point of the port.
 */
@SpringBootTest
@AutoConfigureMockMvc
// The customer API requires authentication from Phase 03 on. These tests are about the customer
// contract, so they run as a user that holds the needed permissions; the token mechanics and the
// permission checks themselves are covered by the auth tests.
@WithMockUser(username = "22222222-2222-2222-2222-222222222222",
        authorities = {"customer:read", "customer:write", "customer:close"})
class CustomerApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerJpaRepository jpaRepository;

    @BeforeEach
    void clearDatabase() {
        // Each test starts from a known state. The data lives in a container shared by the
        // whole JVM, so leftovers from a previous test would make assertions order-dependent.
        jpaRepository.deleteAll();
    }

    private static String createBody(String email) {
        return """
                {
                  "fullName": "Alice Nguyen",
                  "email": "%s",
                  "phoneNumber": "+84 90 123 4567",
                  "dateOfBirth": "1990-01-01"
                }
                """.formatted(email);
    }

    private String createCustomer(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.path("data").path("id").asText();
    }

    @Test
    void shouldSupportTheFullCrudLifecycle() throws Exception {
        String id = createCustomer("lifecycle@example.com");

        // read
        mockMvc.perform(get("/api/v1/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("lifecycle@example.com"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // update
        mockMvc.perform(put("/api/v1/customers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Alice Tran",
                                  "email": "lifecycle.updated@example.com",
                                  "phoneNumber": "+84 91 222 3333"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Alice Tran"))
                .andExpect(jsonPath("$.data.email").value("lifecycle.updated@example.com"));

        // list contains it
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.metadata.totalElements").value(1))
                .andExpect(jsonPath("$.metadata.page").value(0));

        // close
        mockMvc.perform(delete("/api/v1/customers/{id}", id))
                .andExpect(status().isNoContent());

        // closing again is idempotent
        mockMvc.perform(delete("/api/v1/customers/{id}", id))
                .andExpect(status().isNoContent());

        // the record survives the close and is read-only afterwards
        mockMvc.perform(get("/api/v1/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        assertThat(jpaRepository.findById(java.util.UUID.fromString(id))).isPresent();

        mockMvc.perform(put("/api/v1/customers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Should Fail",
                                  "email": "lifecycle.updated@example.com",
                                  "phoneNumber": "+84 91 222 3333"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CUSTOMER_RULE_VIOLATED"));
    }

    @Test
    void shouldRejectADuplicateEmailEndToEnd() throws Exception {
        createCustomer("duplicate@example.com");

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("DUPLICATE@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CUSTOMER_EMAIL_ALREADY_USED"));
    }

    @Test
    void shouldEnforceTheMinimumAgeRuleEndToEnd() throws Exception {
        String underage = """
                {
                  "fullName": "Young Person",
                  "email": "young@example.com",
                  "phoneNumber": "+84 90 123 4567",
                  "dateOfBirth": "2015-01-01"
                }
                """;

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(underage))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CUSTOMER_RULE_VIOLATED"));
    }

    @Test
    void shouldReturn404ForUnknownCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
