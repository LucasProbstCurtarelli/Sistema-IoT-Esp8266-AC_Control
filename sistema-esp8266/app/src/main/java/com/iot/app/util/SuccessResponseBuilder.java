package com.iot.app.util;

import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for building standardized success responses.
 * 
 * Follows Single Responsibility Principle by centralizing success response creation.
 * Reduces code duplication across controllers.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
public final class SuccessResponseBuilder {

    private SuccessResponseBuilder() {
        // Utility class - prevent instantiation
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Builds a standardized success response map.
     * 
     * @param message Success message
     * @param additionalFields Additional fields to include in the response
     * @return Map containing success response
     */
    public static Map<String, Object> buildSuccessResponse(String message, Map<String, Object> additionalFields) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        
        if (additionalFields != null) {
            response.putAll(additionalFields);
        }
        
        return response;
    }

    /**
     * Builds a ResponseEntity with a standardized success response.
     * 
     * @param message Success message
     * @param additionalFields Additional fields to include in the response
     * @return ResponseEntity with success response
     */
    public static ResponseEntity<Map<String, Object>> buildSuccessResponseEntity(
            String message, 
            Map<String, Object> additionalFields) {
        Map<String, Object> response = buildSuccessResponse(message, additionalFields);
        return ResponseEntity.ok(response);
    }
}
