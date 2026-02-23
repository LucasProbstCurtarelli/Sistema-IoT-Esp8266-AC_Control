package com.iot.app.service;

import com.iot.app.constants.ApplicationConstants;
import com.iot.app.dto.LoginRequest;
import com.iot.app.dto.LoginResponse;
import com.iot.app.model.User;
import com.iot.app.repository.UserRepository;
import com.iot.app.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

/**
 * Service for authentication operations.
 * 
 * Follows Single Responsibility Principle by extracting authentication logic
 * from the controller. This improves testability and maintainability.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public AuthService(
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
     * Authenticates a user and generates a JWT token.
     * 
     * @param loginRequest Login credentials
     * @return LoginResponse with token and user info
     * @throws org.springframework.security.authentication.BadCredentialsException if credentials are invalid
     */
    public LoginResponse authenticate(LoginRequest loginRequest) {
        String username = loginRequest.getUsername();
        
        logger.debug("Login attempt for username: {}", username);

        // Authenticate user - throws exception if credentials are invalid
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(username, loginRequest.getPassword())
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String token = jwtTokenProvider.generateToken(userDetails);

        // Get user info from database
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LoginResponse loginResponse = new LoginResponse(true, "Login successful");
        loginResponse.setToken(token);
        
        LoginResponse.UserInfo userInfo = createUserInfo(user);
        loginResponse.setUser(userInfo);
        
        logger.info("User '{}' logged in successfully", username);
        
        return loginResponse;
    }

    /**
     * Gets user information for the authenticated user.
     * 
     * @param username The authenticated username
     * @return UserInfo for the user
     */
    public LoginResponse.UserInfo getUserInfo(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return createUserInfo(user);
    }

    /**
     * Creates UserInfo DTO from User entity.
     * 
     * @param user User entity
     * @return UserInfo DTO
     */
    private LoginResponse.UserInfo createUserInfo(User user) {
        return new LoginResponse.UserInfo(
            user.getId().toString(),
            user.getUsername(),
            user.getUsername() + ApplicationConstants.USER_EMAIL_DOMAIN
        );
    }
}
