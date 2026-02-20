package com.iot.app.security;

import com.iot.app.model.User;
import com.iot.app.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/api/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin((form) -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout((logout) -> logout.permitAll());
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

    @Bean
    public CommandLineRunner initData(UserRepository repo, PasswordEncoder encoder) {
        return args -> {
            // Garante que cria apenas se estiver vazio
            if (repo.count() == 0) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(encoder.encode("admin"));
                admin.setRole("ROLE_ADMIN"); // O Spring gosta do prefixo ROLE_
                repo.save(admin);
                System.out.println("USUÁRIO ADMIN CRIADO COM SUCESSO!"); // Log para você ver acontecendo
            }
        };
    }
}