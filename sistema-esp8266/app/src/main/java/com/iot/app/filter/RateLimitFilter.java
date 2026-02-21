package com.iot.app.filter;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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
    
    private final Bucket loginRateLimiter;
    private final Bucket apiRateLimiter;
    
    // Store buckets per IP address
    private final ConcurrentMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> apiBuckets = new ConcurrentHashMap();

    public RateLimitFilter(
            @Qualifier("loginRateLimiter") Bucket loginRateLimiter,
            @Qualifier("apiRateLimiter") Bucket apiRateLimiter) {
        this.loginRateLimiter = loginRateLimiter;
        this.apiRateLimiter = apiRateLimiter;
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
            // Use per-IP bucket for login endpoint
            bucket = loginBuckets.computeIfAbsent(clientIp, k -> loginRateLimiter);
        } else if (path != null && path.startsWith("/api/")) {
            // Use per-IP bucket for API endpoints
            bucket = apiBuckets.computeIfAbsent(clientIp, k -> apiRateLimiter);
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
                "{\"error\":\"Rate limit exceeded. Please try again later.\"}"
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
