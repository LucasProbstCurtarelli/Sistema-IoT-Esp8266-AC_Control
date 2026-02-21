package com.iot.app.controller;

import com.iot.app.dto.LoginRequest;
import com.iot.app.dto.LoginResponse;
import com.iot.app.model.User;
import com.iot.app.repository.UserRepository;
import com.iot.app.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
    
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            UserDetailsService userDetailsService,
            UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
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
        String password = loginRequest.getPassword();
        
        try {

            // Log login attempt at debug level only (no sensitive information)
            logger.debug("Login attempt for username: {}", username);

            // Authenticate user - throws exception if credentials are invalid
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            String token = jwtTokenProvider.generateToken(userDetails);

            // Set JWT token in httpOnly cookie using ResponseCookie (modern approach)
            // secure flag is environment-based: true in production (HTTPS), false in development (HTTP)
            boolean isProduction = "production".equalsIgnoreCase(System.getenv("SPRING_PROFILES_ACTIVE")) ||
                                   "prod".equalsIgnoreCase(System.getenv("SPRING_PROFILES_ACTIVE"));
            boolean secureCookie = isProduction || "true".equalsIgnoreCase(System.getenv("COOKIE_SECURE"));
            
            ResponseCookie cookie = ResponseCookie
                    .from("authToken", token)
                    .httpOnly(true)
                    .secure(secureCookie)
                    .path("/")
                    .maxAge(86400) // 24 hours
                    .sameSite("Lax") // Lax allows cookies to be sent in top-level navigations
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

            // Get user info from database
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            LoginResponse loginResponse = new LoginResponse(true, "Login realizado com sucesso");
            loginResponse.setToken(token);
            
            LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId().toString(),
                user.getUsername(),
                user.getUsername() + "@automacao.com"
            );
            loginResponse.setUser(userInfo);
            
            logger.info("User '{}' logged in successfully", username);
            
            return ResponseEntity.ok(loginResponse);

        } catch (Exception e) {
            // Log authentication failure at warn level (generic message, no sensitive details)
            logger.warn("Authentication failed for username: {}", username);
            LoginResponse errorResponse = new LoginResponse(false, "Credenciais inválidas");
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
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId().toString(),
                user.getUsername(),
                user.getUsername() + "@automacao.com"
            );
            
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
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Logout successful");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during logout: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Logout failed");
            return ResponseEntity.status(500).body(response);
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
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        // Try cookie
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("authToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        return null;
    }
    
}
