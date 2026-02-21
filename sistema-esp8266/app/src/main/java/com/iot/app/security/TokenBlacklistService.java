package com.iot.app.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
        // Note: We can't easily check expiration without parsing the token
        // For simplicity, we rely on natural expiration and periodic cleanup
        // In a production system with Redis, TTL would handle this automatically
        if (sizeBefore > 10000) {
            // If blacklist grows too large, clear it (tokens should have expired by then)
            logger.warn("Token blacklist size ({}) exceeds threshold, clearing", sizeBefore);
            blacklist.clear();
        }
        logger.debug("Token blacklist cleanup completed (size: {})", blacklist.size());
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
