package com.example.bankcore.security;

import com.example.bankcore.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security checklist from the phase specification, as executable assertions.
 *
 * <p>Each item is a claim the project makes about itself. A claim nothing checks is a claim that
 * stops being true the first time somebody changes a configuration file.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHardeningTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void everyResponseShouldCarryTheHardeningHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; sandbox"))
                .andExpect(header().string("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=()"));
    }

    @Test
    void crossOriginRequestsShouldBeRefusedByDefault() throws Exception {
        // No origin is on the allow-list, so a browser gets no permission to read the response.
        mockMvc.perform(options("/api/v1/customers")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/configprops", "/actuator/beans",
            "/actuator/heapdump", "/actuator/loggers", "/actuator/mappings"})
    void dangerousActuatorEndpointsShouldNotBeExposed(String endpoint) throws Exception {
        // /env and /configprops print configuration including secrets; /heapdump hands over the
        // entire heap. None of them is exposed, so none of them can be reached at all.
        mockMvc.perform(get(endpoint))
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    if (statusCode == 200) {
                        throw new AssertionError(endpoint + " is exposed");
                    }
                });
    }

    @Test
    void protectedEndpointsShouldRefuseAnAbsentToken() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bearer not-a-jwt",
            "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbiJ9.",
            "Bearer ",
            "Basic YWRtaW46YWRtaW4="
    })
    void forgedOrUnsupportedCredentialsShouldBeRefused(String authorization) throws Exception {
        // The second value is the alg=none attack: a token with no signature at all.
        mockMvc.perform(get("/api/v1/customers").header("Authorization", authorization))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anErrorResponseShouldNotLeakInternals() throws Exception {
        String response = mockMvc.perform(get("/api/v1/customers/{id}", "not-a-uuid")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("customer:read"))))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        // No class names, no stack frames, no SQL.
        org.assertj.core.api.Assertions.assertThat(response)
                .doesNotContain("com.example.bankcore")
                .doesNotContain("java.lang")
                .doesNotContain("at org.springframework");
    }

    @Test
    void validationShouldRejectMalformedInputBeforeItReachesTheDomain() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "a", "email": "not-an-email", "password": "x", "fullName": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void aRegistrationResponseShouldNeverEchoThePassword() throws Exception {
        String password = "A-Very-Secret-Passw0rd-Here";

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "headers-check", "email": "headers@example.com",
                                 "password": "%s", "fullName": "Headers Check"}
                                """.formatted(password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
                .doesNotContain(password)
                .doesNotContain("passwordHash")
                .doesNotContain("bcrypt");
    }

    @Test
    void theHttpFirewallShouldRejectSuspiciousRequests() throws Exception {
        // Authenticated, so the 401 does not answer first: the point is that a header carrying
        // control characters is refused as malformed rather than reaching application code.
        mockMvc.perform(get("/api/v1/customers")
                        .header("X-Trace-Id", "bad\nheader")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("customer:read"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void anUnauthenticatedErrorShouldStillUseTheStandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
