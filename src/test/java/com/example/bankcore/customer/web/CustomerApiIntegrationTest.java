package com.example.bankcore.customer.web;

import com.example.bankcore.customer.domain.CustomerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack (controller, validation, service, repository) over real HTTP semantics.
 * Phase 02 replaces the in-memory repository with PostgreSQL behind Testcontainers; this test
 * describes the API contract and should keep passing unchanged.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository repository;

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
                .andExpect(jsonPath("$.metadata.count").isNumber());

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

        assertThat(repository.findById(java.util.UUID.fromString(id))).isPresent();

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
