package com.iot.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for login requests.
 */
@Data
public class LoginRequest {
    
    @NotBlank(message = "Username é obrigatório")
    private String username;
    
    @NotBlank(message = "Senha é obrigatória")
    private String password;
}
