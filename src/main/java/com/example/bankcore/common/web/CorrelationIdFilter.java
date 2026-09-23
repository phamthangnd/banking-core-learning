package com.example.bankcore.common.web;

import com.example.bankcore.common.trace.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Gives every request a correlation id and echoes it back to the caller.
 *
 * <p>An inbound {@code X-Trace-Id} is honoured so a call chain keeps one id end to end; otherwise
 * a new one is generated. The id is put into the MDC before the request is handled and removed
 * afterwards — servlet threads are pooled, so a forgotten id would leak into the next, unrelated
 * request.
 *
 * <p>The inbound value is length-limited and sanitised: it comes from an untrusted client and
 * ends up in log lines, which must not be forgeable or injectable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = sanitize(request.getHeader(CorrelationId.HEADER));
        CorrelationId.set(traceId);
        response.setHeader(CorrelationId.HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationId.clear();
        }
    }

    private static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return CorrelationId.generate();
        }

        String trimmed = candidate.strip();
        if (trimmed.length() > MAX_LENGTH || !trimmed.matches("[A-Za-z0-9_.:-]+")) {
            return CorrelationId.generate();
        }
        return trimmed;
    }
}
