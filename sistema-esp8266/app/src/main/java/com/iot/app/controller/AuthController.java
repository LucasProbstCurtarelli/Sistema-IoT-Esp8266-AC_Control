package com.iot.app.controller;

import com.iot.app.dto.LoginRequest;
import com.iot.app.dto.LoginResponse;
import com.iot.app.model.User;
import com.iot.app.repository.UserRepository;
import com.iot.app.security.JwtTokenProvider;
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
        
        try {
            String username = loginRequest.getUsername();
            String password = loginRequest.getPassword();

            // Debug: verificar se o usuário existe no banco
            logger.info("Attempting login for username: {}", username);
            userRepository.findByUsername(username).ifPresentOrElse(
                user -> {
                    logger.info("User found in database: username={}, role={}, passwordHash length={}, passwordHash prefix={}", 
                        user.getUsername(), user.getRole(), 
                        user.getPassword() != null ? user.getPassword().length() : 0,
                        user.getPassword() != null && user.getPassword().length() > 7 ? user.getPassword().substring(0, 7) : "null");
                },
                () -> {
                    logger.error("User '{}' NOT FOUND in database. Available users: {}", username, 
                        userRepository.findAll().stream().map(u -> u.getUsername()).toList());
                }
            );

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
            logger.error("Login failed: {}", e.getMessage());
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
    
}
