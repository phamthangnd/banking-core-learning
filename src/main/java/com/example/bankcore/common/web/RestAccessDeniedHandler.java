package com.example.bankcore.common.web;

import com.example.bankcore.common.api.ApiError;
import com.example.bankcore.common.api.ApiResponse;
import com.example.bankcore.common.api.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answers an authenticated-but-forbidden request in the application's own envelope.
 *
 * <p>A denied authorization is worth logging with the principal: unlike a failed login, it means
 * a known account attempted something outside its role, which is exactly the signal an audit
 * trail should carry (Phase 07 turns this into a real audit event).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("Access denied: principal={} {} {}",
                authentication == null ? "anonymous" : authentication.getName(),
                request.getMethod(), request.getRequestURI());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(
                ApiError.of(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action")));
    }
}
