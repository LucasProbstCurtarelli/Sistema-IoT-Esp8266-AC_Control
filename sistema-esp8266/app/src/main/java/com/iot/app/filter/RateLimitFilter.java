package com.iot.app.filter;

import com.iot.app.constants.ApplicationConstants;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiting filter using Bucket4j.
 * 
 * Tracks rate limits per IP address and applies different limits
 * for login endpoint vs general API endpoints.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);
    
    // Store buckets per IP address (each IP gets its own bucket instance)
    private final ConcurrentMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> apiBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter() {
        // No dependencies needed - buckets are created per-IP
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String clientIp = getClientIpAddress(request);
        
        Bucket bucket;
        boolean isLoginEndpoint = path != null && path.contains("/api/login");
        
        if (isLoginEndpoint) {
            // Create per-IP bucket for login endpoint
            bucket = loginBuckets.computeIfAbsent(clientIp, k -> Bucket.builder()
                    .addLimit(Bandwidth.classic(
                        ApplicationConstants.LOGIN_RATE_LIMIT_REQUESTS, 
                        Refill.intervally(
                            ApplicationConstants.LOGIN_RATE_LIMIT_REQUESTS, 
                            Duration.ofMinutes(ApplicationConstants.LOGIN_RATE_LIMIT_WINDOW_MINUTES)
                        )
                    ))
                    .build());
        } else if (path != null && path.startsWith("/api/")) {
            // Create per-IP bucket for API endpoints
            bucket = apiBuckets.computeIfAbsent(clientIp, k -> Bucket.builder()
                    .addLimit(Bandwidth.classic(
                        ApplicationConstants.API_RATE_LIMIT_REQUESTS, 
                        Refill.intervally(
                            ApplicationConstants.API_RATE_LIMIT_REQUESTS, 
                            Duration.ofMinutes(ApplicationConstants.API_RATE_LIMIT_WINDOW_MINUTES)
                        )
                    ))
                    .build());
        } else {
            // No rate limiting for other endpoints
            filterChain.doFilter(request, response);
            return;
        }
        
        // Try to consume a token
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            logger.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                String.format("{\"error\":\"%s\"}", ApplicationConstants.RATE_LIMIT_EXCEEDED)
            );
        }
    }
    
    /**
     * Gets the client IP address from the request.
     * Handles proxies and load balancers.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
