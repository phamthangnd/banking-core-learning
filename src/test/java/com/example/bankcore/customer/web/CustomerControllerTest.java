package com.example.bankcore.customer.web;

import com.example.bankcore.common.config.SecurityConfig;
import com.example.bankcore.common.pagination.PageResult;
import com.example.bankcore.common.trace.CorrelationId;
import com.example.bankcore.common.web.CorrelationIdFilter;
import com.example.bankcore.common.web.GlobalExceptionHandler;
import com.example.bankcore.customer.application.CustomerService;
import com.example.bankcore.customer.config.CustomerProperties;
import com.example.bankcore.customer.domain.Customer;
import com.example.bankcore.customer.domain.CustomerEmailAlreadyUsedException;
import com.example.bankcore.customer.domain.CustomerNotFoundException;
import com.example.bankcore.customer.domain.CustomerRuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
@Import({SecurityConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class,
        com.example.bankcore.common.web.RestAuthenticationEntryPoint.class,
        com.example.bankcore.common.web.RestAccessDeniedHandler.class})
@EnableConfigurationProperties(CustomerProperties.class)
@ActiveProfiles("test")
// Every test in this class acts as a user holding the customer permissions. Authorization
// itself is verified end to end in CustomerAuthorizationIntegrationTest, against real tokens.
@WithMockUser(username = "11111111-1111-1111-1111-111111111111",
        authorities = {"customer:read", "customer:write", "customer:close"})
class CustomerControllerTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    /** The web slice does not build the real decoder; the security chain still requires the bean. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
            jwtAuthenticationConverter;

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
    void shouldReturnAPageOfCustomersWithNavigationMetadata() throws Exception {
        given(customerService.search(any()))
                .willReturn(new PageResult<>(List.of(customer()), 0, 20, 45));

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.metadata.page").value(0))
                .andExpect(jsonPath("$.metadata.size").value(20))
                .andExpect(jsonPath("$.metadata.totalElements").value(45))
                .andExpect(jsonPath("$.metadata.totalPages").value(3))
                .andExpect(jsonPath("$.metadata.hasNext").value(true));
    }

    @Test
    void shouldPassFiltersAndSortingToTheService() throws Exception {
        given(customerService.search(any())).willReturn(new PageResult<>(List.of(), 1, 5, 0));

        mockMvc.perform(get("/api/v1/customers")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "FULL_NAME")
                        .param("direction", "ASC")
                        .param("name", "alice")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.example.bankcore.customer.domain.CustomerSearchQuery.class);
        verify(customerService).search(captor.capture());

        var query = captor.getValue();
        assertThat(query.nameFragment()).isEqualTo("alice");
        assertThat(query.status()).isEqualTo(com.example.bankcore.customer.domain.CustomerStatus.ACTIVE);
        assertThat(query.page().page()).isEqualTo(1);
        assertThat(query.page().size()).isEqualTo(5);
        assertThat(query.page().sortProperty()).isEqualTo("fullName");
        assertThat(query.page().direction())
                .isEqualTo(com.example.bankcore.common.pagination.SortDirection.ASC);
    }

    @Test
    void shouldRejectAnUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/v1/customers").param("sort", "PASSWORD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("sort"))
                .andExpect(jsonPath("$.error.details[0].message").value("has an invalid value"))
                // The message must not expose internal class names.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("com.example.bankcore"))));
    }

    @Test
    void shouldRejectANegativePageIndex() throws Exception {
        mockMvc.perform(get("/api/v1/customers").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("page"));
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
        given(customerService.search(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/customers").header(CorrelationId.HEADER, "trace-12345"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER, "trace-12345"))
                .andExpect(jsonPath("$.traceId").value("trace-12345"));
    }

    @Test
    void shouldReplaceACorrelationIdWithDisallowedCharacters() throws Exception {
        given(customerService.search(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        // Passes the HTTP firewall (no control characters) but is not an acceptable id:
        // it would end up in log lines, so the filter swaps it for a generated one.
        String hostile = "<script>alert(1)</script>";

        mockMvc.perform(get("/api/v1/customers").header(CorrelationId.HEADER, hostile))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER, org.hamcrest.Matchers.not(hostile)))
                .andExpect(jsonPath("$.traceId").value(org.hamcrest.Matchers.not(hostile)));
    }

    @Test
    void shouldReplaceAnOverlongCorrelationId() throws Exception {
        given(customerService.search(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        String tooLong = "a".repeat(200);

        mockMvc.perform(get("/api/v1/customers").header(CorrelationId.HEADER, tooLong))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER, org.hamcrest.Matchers.not(tooLong)));
    }

    @Test
    void shouldRejectAHeaderWithControlCharactersAsBadRequest() throws Exception {
        // Defence in depth: Spring Security's StrictHttpFirewall stops this before any
        // application code runs. It must answer 400, not leak a 500.
        mockMvc.perform(get("/api/v1/customers").header(CorrelationId.HEADER, "bad id\nwith newline"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }
}
