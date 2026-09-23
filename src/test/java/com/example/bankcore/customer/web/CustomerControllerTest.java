package com.example.bankcore.customer.web;

import com.example.bankcore.common.config.SecurityConfig;
import com.example.bankcore.common.trace.CorrelationId;
import com.example.bankcore.common.web.CorrelationIdFilter;
import com.example.bankcore.common.web.GlobalExceptionHandler;
import com.example.bankcore.customer.application.CustomerService;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerEmailAlreadyUsedException;
import com.example.bankcore.customer.domain.CustomerNotFoundException;
import com.example.bankcore.customer.domain.CustomerRuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer only: the service is mocked, so these tests prove HTTP semantics, the response
 * envelope and the exception-to-status mapping rather than business rules.
 */
@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class})
@ActiveProfiles("test")
class CustomerControllerTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    private static Customer customer() {
        return Customer.register(ID, "Alice Nguyen", "alice@example.com", "+84 90 123 4567",
                LocalDate.of(1990, 1, 1), NOW);
    }

    private static String createBody() {
        return """
                {
                  "fullName": "Alice Nguyen",
                  "email": "alice@example.com",
                  "phoneNumber": "+84 90 123 4567",
                  "dateOfBirth": "1990-01-01"
                }
                """;
    }

    @Test
    void shouldCreateCustomerAndReturnLocation() throws Exception {
        given(customerService.create(any())).willReturn(customer());

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/customers/" + ID))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ID.toString()))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void shouldReturnStandardizedValidationErrors() throws Exception {
        String invalid = """
                {
                  "fullName": "  ",
                  "email": "not-an-email",
                  "phoneNumber": "abc",
                  "dateOfBirth": "2999-01-01"
                }
                """;

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.length()").value(4))
                .andExpect(jsonPath("$.error.details[0].field").value("dateOfBirth"))
                .andExpect(jsonPath("$.error.details[1].field").value("email"))
                .andExpect(jsonPath("$.error.details[2].field").value("fullName"))
                .andExpect(jsonPath("$.error.details[3].field").value("phoneNumber"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void shouldNotEchoRejectedValuesInValidationErrors() throws Exception {
        String invalid = """
                {
                  "fullName": "Alice",
                  "email": "super-secret-token-value",
                  "phoneNumber": "+84 90 123 4567",
                  "dateOfBirth": "1990-01-01"
                }
                """;

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("super-secret-token-value"))));
    }

    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void shouldMapNotFoundTo404() throws Exception {
        given(customerService.getById(ID)).willThrow(new CustomerNotFoundException(ID));

        mockMvc.perform(get("/api/v1/customers/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void shouldMapDuplicateEmailTo409() throws Exception {
        willThrow(new CustomerEmailAlreadyUsedException()).given(customerService).create(any());

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CUSTOMER_EMAIL_ALREADY_USED"));
    }

    @Test
    void shouldMapBusinessRuleViolationTo422() throws Exception {
        willThrow(new CustomerRuleViolationException("Customer must be at least 18 years old"))
                .given(customerService).create(any());

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CUSTOMER_RULE_VIOLATED"))
                .andExpect(jsonPath("$.error.message").value("Customer must be at least 18 years old"));
    }

    @Test
    void shouldRejectMalformedIdentifier() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("id"));
    }

    @Test
    void shouldListCustomersWithMetadata() throws Exception {
        given(customerService.list()).willReturn(List.of(customer()));

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.metadata.count").value(1))
                .andExpect(jsonPath("$.metadata.paginated").value(false));
    }

    @Test
    void shouldUpdateCustomer() throws Exception {
        given(customerService.update(eq(ID), any())).willReturn(customer());

        String body = """
                {
                  "fullName": "Alice Nguyen",
                  "email": "alice@example.com",
                  "phoneNumber": "+84 90 123 4567"
                }
                """;

        mockMvc.perform(put("/api/v1/customers/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ID.toString()));
    }

    @Test
    void shouldCloseCustomerWithNoContent() throws Exception {
        given(customerService.close(ID)).willReturn(customer().close(NOW));

        mockMvc.perform(delete("/api/v1/customers/{id}", ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(customerService).close(ID);
    }

    @Test
    void shouldEchoAnInboundCorrelationId() throws Exception {
        given(customerService.list()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/customers").header(CorrelationId.HEADER, "trace-12345"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER, "trace-12345"))
                .andExpect(jsonPath("$.traceId").value("trace-12345"));
    }

    @Test
    void shouldReplaceAnUntrustedCorrelationId() throws Exception {
        given(customerService.list()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/customers").header(CorrelationId.HEADER, "bad id\nwith newline"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER,
                        org.hamcrest.Matchers.not("bad id\nwith newline")));
    }
}
