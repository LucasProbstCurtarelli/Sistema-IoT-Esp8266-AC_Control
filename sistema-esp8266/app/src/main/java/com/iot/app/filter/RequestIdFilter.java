package com.iot.app.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that adds a unique request ID to each HTTP request for log correlation.
 * 
 * The request ID is:
 * - Added to the MDC (Mapped Diagnostic Context) for automatic inclusion in logs
 * - Set as an HTTP response header (X-Request-ID) for client correlation
 * 
 * This enables tracking requests across services and correlating logs.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        // Generate or use existing request ID from header
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        
        // Add to MDC for automatic log inclusion
        MDC.put(MDC_REQUEST_ID_KEY, requestId);
        
        // Set response header
        response.setHeader(REQUEST_ID_HEADER, requestId);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Clear MDC to prevent memory leaks
            MDC.clear();
        }
    }
}
