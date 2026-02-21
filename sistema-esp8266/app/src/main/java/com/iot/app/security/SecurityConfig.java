package com.iot.app.security;

import com.iot.app.model.User;
import com.iot.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfigurationSource;
import com.iot.app.filter.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.regex.Pattern;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 1. A PONTE QUE FALTAVA: Ensina o Spring a buscar o usuário no banco
    @Bean
    public UserDetailsService userDetailsService(UserRepository repo) {
        return username -> {
            User user = repo.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + username));

            // Converte o seu User (do banco) para o UserDetails (do Spring)
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPassword())
                    .authorities(user.getRole()) // ou roles(user.getRole()) dependendo de como salvou ("ROLE_ADMIN" vs
                                                 // "ADMIN")
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, 
            CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> {
                    // Disable CSRF for API endpoints (stateless JWT authentication)
                    // CSRF protection is not needed for stateless APIs using JWT tokens
                    // JWT tokens in httpOnly cookies with SameSite=Lax provide sufficient protection
                    csrf.ignoringRequestMatchers("/api/**");
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests((requests) -> requests
                        // Public endpoints
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/api/login").permitAll()
                        // Temporary debug endpoint (remove in production)
                        .requestMatchers("/api/debug/**").permitAll()
                        // Admin endpoints require ADMIN role
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // All other API endpoints require authentication
                        .requestMatchers("/api/**").authenticated()
                        // All other requests require authentication
                        .anyRequest().authenticated())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        // Return 401 (Unauthorized) when authentication is required but not provided
                        .authenticationEntryPoint(authenticationEntryPoint())
                        // Return 403 (Forbidden) when authenticated but lacks required authority
                        .accessDeniedHandler(accessDeniedHandler()))
                .formLogin(form -> form.disable()) // Disable form login since we use JWT
                .logout(logout -> logout.disable()); // Disable default logout, implement custom if needed
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Returns 401 (Unauthorized) when authentication is required but not provided.
     * This is different from 403 (Forbidden), which means the user is authenticated but lacks permission.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            logger.debug("Authentication required but not provided: {}", authException.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
        };
    }

    /**
     * Returns 403 (Forbidden) when the user is authenticated but lacks required authority.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            logger.debug("Access denied: {}", accessDeniedException.getMessage());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Insufficient permissions\"}");
        };
    }

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    
    // Password strength requirements
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{" + MIN_PASSWORD_LENGTH + ",}$"
    );

    /**
     * Validates password strength.
     * Requirements: at least 8 characters, contains uppercase, lowercase, digit, and special character.
     */
    private boolean isValidPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(password).matches();
    }

    @Bean
    public CommandLineRunner initData(UserRepository repo, PasswordEncoder encoder) {
        return args -> {
            // Check if admin user exists and ensure password is correct
            String profile = System.getenv("SPRING_PROFILES_ACTIVE");
            if (profile == null || profile.isEmpty()) {
                profile = System.getProperty("spring.profiles.active", "dev");
            }
            boolean isDevelopment = "dev".equalsIgnoreCase(profile) || "development".equalsIgnoreCase(profile);
            
            if (isDevelopment) {
                // In development, always ensure admin password is "admin"
                repo.findByUsername("admin").ifPresentOrElse(
                    admin -> {
                        String correctPassword = "admin";
                        // Test if current hash matches "admin"
                        if (!encoder.matches(correctPassword, admin.getPassword())) {
                            logger.warn("Admin password hash doesn't match 'admin'. Updating...");
                            String newHash = encoder.encode(correctPassword);
                            // Use update query to avoid optimistic locking issues
                            int updated = repo.updatePasswordByUsername("admin", newHash);
                            if (updated > 0) {
                                logger.info("Admin password updated to 'admin'");
                            } else {
                                logger.warn("Failed to update admin password");
                            }
                        } else {
                            logger.debug("Admin password hash is correct");
                        }
                    },
                    () -> logger.debug("Admin user not found, will be created if database is empty")
                );
            }
            
            // Only create admin user if database is empty
            if (repo.count() == 0) {
                String adminUsername = System.getenv("ADMIN_USERNAME");
                String adminPassword = System.getenv("ADMIN_PASSWORD");
                
                // Use environment variables, fallback to defaults only in development
                if (adminUsername == null || adminUsername.isEmpty()) {
                    if (isDevelopment) {
                        adminUsername = "admin";
                        logger.warn("Using default admin username 'admin' in development mode. " +
                                   "Set ADMIN_USERNAME environment variable for production.");
                    } else {
                        logger.error("ADMIN_USERNAME environment variable is required in production!");
                        throw new IllegalStateException(
                            "ADMIN_USERNAME environment variable must be set in production. " +
                            "Set ADMIN_USERNAME and ADMIN_PASSWORD before starting the application."
                        );
                    }
                }
                
                if (adminPassword == null || adminPassword.isEmpty()) {
                    if (isDevelopment) {
                        adminPassword = "admin"; // Simple default for development (matches init_database.sql)
                        logger.warn("Using default admin password 'admin' in development mode. " +
                                   "Set ADMIN_PASSWORD environment variable for production.");
                    } else {
                        logger.error("ADMIN_PASSWORD environment variable is required in production!");
                        throw new IllegalStateException(
                            "ADMIN_PASSWORD environment variable must be set in production. " +
                            "Password must be at least 8 characters with uppercase, lowercase, digit, and special character."
                        );
                    }
                }
                
                // Validate password strength (skip in development for simple passwords like "admin")
                if (!isDevelopment && !isValidPassword(adminPassword)) {
                    String errorMsg = String.format(
                        "Admin password does not meet strength requirements. " +
                        "Password must be at least %d characters and contain: " +
                        "uppercase letter, lowercase letter, digit, and special character (@$!%%*?&).",
                        MIN_PASSWORD_LENGTH
                    );
                    logger.error(errorMsg);
                    throw new IllegalStateException(errorMsg);
                }
                
                User admin = new User();
                admin.setUsername(adminUsername);
                admin.setPassword(encoder.encode(adminPassword));
                admin.setRole("ROLE_ADMIN");
                repo.save(admin);
                
                logger.info("Admin user '{}' created successfully", adminUsername);
            }
        };
    }
}