package com.iot.app.security;

import com.iot.app.constants.ApplicationConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing JWT token blacklist (revocation).
 * 
 * Provides a simple in-memory token revocation mechanism.
 * Tokens are stored in a blacklist until they expire naturally.
 * 
 * For production use with multiple instances, consider using Redis or a shared cache.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
@Service
public class TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);
    
    @Value("${jwt.secret:}")
    private String jwtSecret;
    
    /**
     * In-memory blacklist of revoked tokens.
     * Key: token (full JWT string), Value: expiration timestamp
     */
    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();
    
    /**
     * Scheduled executor for periodic cleanup of expired tokens from blacklist.
     */
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "token-blacklist-cleanup");
        t.setDaemon(true);
        return t;
    });
    
    /**
     * Constructor that starts periodic cleanup task.
     */
    public TokenBlacklistService() {
        // Clean up expired tokens every hour
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredTokens, 1, 1, TimeUnit.HOURS);
        logger.info("Token blacklist service initialized with periodic cleanup");
    }
    
    /**
     * Revokes a token by adding it to the blacklist.
     * 
     * @param token The JWT token to revoke
     */
    public void revokeToken(String token) {
        if (token != null && !token.isEmpty()) {
            blacklist.add(token);
            logger.debug("Token revoked (blacklist size: {})", blacklist.size());
        }
    }
    
    /**
     * Checks if a token is blacklisted (revoked).
     * 
     * @param token The JWT token to check
     * @return true if the token is blacklisted, false otherwise
     */
    public boolean isTokenRevoked(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return blacklist.contains(token);
    }
    
    /**
     * Removes expired tokens from the blacklist to prevent memory leaks.
     * This is called periodically by the cleanup executor.
     */
    private void cleanupExpiredTokens() {
        int sizeBefore = blacklist.size();
        if (sizeBefore == 0) {
            return;
        }
        
        Date now = new Date();
        int removedCount = 0;
        
        // Parse tokens and remove only expired ones
        Iterator<String> iterator = blacklist.iterator();
        while (iterator.hasNext()) {
            String token = iterator.next();
            try {
                Date expiration = extractExpiration(token);
                if (expiration != null && expiration.before(now)) {
                    iterator.remove();
                    removedCount++;
                }
            } catch (Exception e) {
                // If token parsing fails, remove it (invalid token)
                iterator.remove();
                removedCount++;
                logger.debug("Removed invalid token during cleanup: {}", e.getMessage());
            }
        }
        
        if (removedCount > 0) {
            logger.debug("Token blacklist cleanup: removed {} expired tokens (size: {} -> {})", 
                    removedCount, sizeBefore, blacklist.size());
        }
        
        // Fallback: if blacklist still grows too large, clear it
        if (blacklist.size() > ApplicationConstants.TOKEN_BLACKLIST_MAX_SIZE) {
            logger.warn("Token blacklist size ({}) exceeds threshold, clearing all entries", blacklist.size());
            blacklist.clear();
        }
    }
    
    /**
     * Extracts expiration date from JWT token.
     * 
     * @param token The JWT token
     * @return Expiration date, or null if parsing fails
     */
    private Date extractExpiration(String token) {
        try {
            // Get secret from environment or use default for dev
            String secret = jwtSecret;
            if (secret == null || secret.isEmpty()) {
                String envSecret = System.getenv("JWT_SECRET");
                if (envSecret != null && !envSecret.isEmpty()) {
                    secret = envSecret;
                } else {
                    // Fallback to dev secret for parsing only
                    secret = "dev-secret-key-minimum-32-characters-long-for-testing-only-do-not-use-in-production";
                }
            }
            
            SecretKey signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return claims.getExpiration();
        } catch (Exception e) {
            logger.debug("Failed to extract expiration from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the current size of the blacklist.
     * 
     * @return The number of tokens in the blacklist
     */
    public int getBlacklistSize() {
        return blacklist.size();
    }
}
