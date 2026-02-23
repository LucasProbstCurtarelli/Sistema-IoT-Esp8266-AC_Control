package com.iot.app.controller;

import com.iot.app.constants.ApplicationConstants;
import com.iot.app.dto.LoginRequest;
import com.iot.app.dto.LoginResponse;
import com.iot.app.security.JwtTokenProvider;
import com.iot.app.service.AuthService;
import com.iot.app.util.ErrorResponseBuilder;
import com.iot.app.util.SuccessResponseBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for authentication.
 * 
 * Provides login endpoint for the Next.js frontend.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthService authService, JwtTokenProvider jwtTokenProvider) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Endpoint for user login.
     * 
     * @param loginRequest DTO containing username and password
     * @param response HTTP response to set cookie
     * @return HTTP response with authentication result and JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletResponse response) {
        
        String username = loginRequest.getUsername();
        
        try {
            LoginResponse loginResponse = authService.authenticate(loginRequest);
            String token = loginResponse.getToken();

            // Set JWT token in httpOnly cookie using ResponseCookie (modern approach)
            // secure flag is environment-based: true in production (HTTPS), false in development (HTTP)
            boolean isProduction = "production".equalsIgnoreCase(System.getenv("SPRING_PROFILES_ACTIVE")) ||
                                   "prod".equalsIgnoreCase(System.getenv("SPRING_PROFILES_ACTIVE"));
            boolean secureCookie = isProduction || "true".equalsIgnoreCase(System.getenv("COOKIE_SECURE"));
            
            ResponseCookie cookie = ResponseCookie
                    .from(ApplicationConstants.AUTH_TOKEN_COOKIE_NAME, token)
                    .httpOnly(true)
                    .secure(secureCookie)
                    .path("/")
                    .maxAge(ApplicationConstants.COOKIE_MAX_AGE_SECONDS)
                    .sameSite("Lax") // Lax allows cookies to be sent in top-level navigations
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            
            return ResponseEntity.ok(loginResponse);

        } catch (Exception e) {
            // Log authentication failure at warn level (generic message, no sensitive details)
            logger.warn("Authentication failed for username: {}", username);
            LoginResponse errorResponse = new LoginResponse(false, "Invalid credentials");
            return ResponseEntity.status(401).body(errorResponse);
        }
    }
    
    /**
     * Endpoint to get current user information.
     * 
     * @return HTTP response with current user information
     */
    @GetMapping("/me")
    public ResponseEntity<LoginResponse.UserInfo> getCurrentUser() {
        // User is already authenticated via JWT filter
        try {
            // Get authenticated user from security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).build();
            }
            
            String username = authentication.getName();
            LoginResponse.UserInfo userInfo = authService.getUserInfo(username);
            
            return ResponseEntity.ok(userInfo);
            
        } catch (Exception e) {
            logger.error("Error getting current user: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
    
    /**
     * Endpoint to logout (revoke current token).
     * 
     * @param request HTTP request to extract token
     * @return HTTP response indicating logout success
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        try {
            // Extract token from request
            String token = extractTokenFromRequest(request);
            
            if (token != null) {
                // Revoke the token
                jwtTokenProvider.revokeToken(token);
                logger.info("Token revoked for user logout");
            }
            
            return SuccessResponseBuilder.buildSuccessResponseEntity(
                "Logout successful", 
                null
            );
            
        } catch (Exception e) {
            logger.error("Error during logout: {}", e.getMessage());
            return ErrorResponseBuilder.buildErrorResponseEntity(
                false, 
                "Logout failed", 
                500
            );
        }
    }
    
    /**
     * Extracts JWT token from HTTP request (cookie or Authorization header).
     * 
     * @param request The HTTP request
     * @return The JWT token, or null if not found
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        // Try Authorization header first
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith(ApplicationConstants.BEARER_PREFIX)) {
            return bearerToken.substring(ApplicationConstants.BEARER_PREFIX.length());
        }
        
        // Try cookie
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if (ApplicationConstants.AUTH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        return null;
    }
    
}
