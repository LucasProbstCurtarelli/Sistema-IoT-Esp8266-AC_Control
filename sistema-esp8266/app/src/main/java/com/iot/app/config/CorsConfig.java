package com.iot.app.config;

import com.iot.app.constants.ApplicationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CORS configuration to allow Next.js frontend to communicate with Spring Boot backend.
 * Configured via CORS_ALLOWED_ORIGINS environment variable.
 */
@Configuration
public class CorsConfig {

    private static final Logger logger = LoggerFactory.getLogger(CorsConfig.class);
    
    @Value("${cors.allowed.origins:http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOriginsConfig;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Read allowed origins from environment variable or property
        String envOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        String originsConfig = envOrigins != null && !envOrigins.isEmpty() 
            ? envOrigins 
            : allowedOriginsConfig;
        
        // Parse comma-separated origins
        List<String> allowedOrigins = Arrays.stream(originsConfig.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .collect(Collectors.toList());
        
        if (allowedOrigins.isEmpty()) {
            // Fallback to default for development
            allowedOrigins = List.of("http://localhost:3000", "http://127.0.0.1:3000");
            logger.warn("No CORS origins configured, using defaults: {}", allowedOrigins);
        } else {
            logger.info("CORS allowed origins: {}", allowedOrigins);
        }
        
        configuration.setAllowedOrigins(allowedOrigins);
        
        // Allow common HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        // Allow common headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "X-CSRF-TOKEN"
        ));
        
        // Allow credentials (cookies, auth headers)
        configuration.setAllowCredentials(true);
        
        // Cache preflight requests
        configuration.setMaxAge((long) ApplicationConstants.CORS_MAX_AGE_SECONDS);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
