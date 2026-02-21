package com.iot.app.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Rate limiting configuration using Bucket4j.
 * 
 * Configured limits:
 * - Login endpoint: 5 requests per 15 minutes per IP
 * - API endpoints: 100 requests per minute per IP
 */
@Configuration
public class RateLimitConfig {

    /**
     * Rate limiter for login endpoint.
     * Allows 5 requests per 15 minutes.
     */
    @Bean(name = "loginRateLimiter")
    public Bucket loginRateLimiter() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(15))))
                .build();
    }

    /**
     * Rate limiter for general API endpoints.
     * Allows 100 requests per minute.
     */
    @Bean(name = "apiRateLimiter")
    public Bucket apiRateLimiter() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
                .build();
    }
}
