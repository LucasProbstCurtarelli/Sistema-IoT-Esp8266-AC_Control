package com.iot.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Data Transfer Object for login requests.
 * 
 * Validates username and password with length constraints.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
@Data
public class LoginRequest {
    
    /**
     * Username for authentication.
     * Must be between 5 and 25 characters.
     */
    @NotBlank(message = "Username is required")
    @Size(min = 5, max = 25, message = "Username must be between 5 and 25 characters")
    private String username;
    
    /**
     * Password for authentication.
     * Must be between 7 and 25 characters.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 7, max = 25, message = "Password must be between 7 and 25 characters")
    private String password;
}
