package com.iot.app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT Token Provider for generating and validating JWT tokens.
 * 
 * Handles token creation, validation, and revocation checking.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String DEFAULT_SECRET = "your-256-bit-secret-key-change-this-in-production-minimum-32-characters";
    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}") // 24 hours default
    private long jwtExpirationMs;
    
    private final TokenBlacklistService tokenBlacklistService;
    
    public JwtTokenProvider(TokenBlacklistService tokenBlacklistService) {
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @PostConstruct
    public void validateSecret() {
        // Check if JWT_SECRET environment variable is set
        String envSecret = System.getenv("JWT_SECRET");
        if (envSecret != null && !envSecret.isEmpty()) {
            jwtSecret = envSecret;
        }
        
        // Check Spring profile - allow default in dev mode only
        String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (activeProfile == null || activeProfile.isEmpty()) {
            activeProfile = System.getProperty("spring.profiles.active", "dev");
        }
        boolean isDevelopment = "dev".equalsIgnoreCase(activeProfile) || "development".equalsIgnoreCase(activeProfile);
        
        // Fail fast if secret is missing or invalid
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            if (isDevelopment) {
                // Use a default secret for development only
                jwtSecret = "dev-secret-key-minimum-32-characters-long-for-testing-only-do-not-use-in-production";
                logger.warn("JWT_SECRET not set. Using default development secret. " +
                           "Set JWT_SECRET environment variable for production!");
            } else {
                String errorMsg = "JWT_SECRET environment variable is required and must be at least " + 
                                MIN_SECRET_LENGTH + " characters long. " +
                                "Set JWT_SECRET environment variable before starting the application.";
                logger.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
        } else if (jwtSecret.equals(DEFAULT_SECRET)) {
            if (isDevelopment) {
                // Allow default in dev mode but warn
                logger.warn("Using default JWT_SECRET. This is insecure for production!");
            } else {
                String errorMsg = "Default JWT_SECRET cannot be used in production. " +
                                "Set JWT_SECRET environment variable with a secure value.";
                logger.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
        }
        
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            String errorMsg = "JWT_SECRET must be at least " + MIN_SECRET_LENGTH + 
                            " characters long. Current length: " + jwtSecret.length();
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        logger.info("JWT secret validated successfully (length: {})", jwtSecret.length());
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validates a JWT token against user details and checks if it's revoked.
     * 
     * @param token The JWT token to validate
     * @param userDetails The user details to validate against
     * @return true if the token is valid and not revoked, false otherwise
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        // Check if token is revoked (blacklisted)
        if (tokenBlacklistService.isTokenRevoked(token)) {
            logger.debug("Token validation failed: token is revoked");
            return false;
        }
        
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
    
    /**
     * Revokes a token by adding it to the blacklist.
     * 
     * @param token The JWT token to revoke
     */
    public void revokeToken(String token) {
        tokenBlacklistService.revokeToken(token);
    }
}
