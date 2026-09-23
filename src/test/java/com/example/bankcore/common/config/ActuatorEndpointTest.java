package com.example.bankcore.common.config;

import com.example.bankcore.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 00 acceptance: the application exposes a health endpoint, and nothing else is public
 * by accident.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorEndpointTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointShouldBePubliclyReachableAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthEndpointShouldNotLeakDetailsToAnonymousCallers() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void infoEndpointShouldBeReachable() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownEndpointsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void theCustomerApiShouldNoLongerBePublic() throws Exception {
        // Phase 01 opened this path without authentication; Phase 03 closes it.
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized());
    }
}
