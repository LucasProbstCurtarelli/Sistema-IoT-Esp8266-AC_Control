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
     * @param request HTTP request
     * @param response HTTP response to set cookie
     * @return HTTP response with authentication result and JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        try {
            String username = loginRequest.getUsername();
            String password = loginRequest.getPassword();

            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            String token = jwtTokenProvider.generateToken(userDetails);

            // Set JWT token in httpOnly cookie using ResponseCookie (modern approach)
            // secure=false for development (HTTP), set to true in production (HTTPS)
            ResponseCookie cookie = ResponseCookie
                    .from("authToken", token)
                    .httpOnly(true)
                    .secure(false) // false for development (HTTP), true for production (HTTPS)
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
}
