package com.iot.app.util;

import com.iot.app.constants.ApplicationConstants;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for building standardized error responses.
 * 
 * Follows Single Responsibility Principle by centralizing error response creation.
 * Reduces code duplication across controllers.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
public final class ErrorResponseBuilder {

    private ErrorResponseBuilder() {
        // Utility class - prevent instantiation
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Builds a standardized error response map.
     * 
     * @param success Whether the operation was successful
     * @param message Error message
     * @return Map containing error response
     */
    public static Map<String, Object> buildErrorResponse(boolean success, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        return response;
    }

    /**
     * Builds a standardized error response map with additional fields.
     * 
     * @param success Whether the operation was successful
     * @param message Error message
     * @param additionalFields Additional fields to include in the response
     * @return Map containing error response
     */
    public static Map<String, Object> buildErrorResponse(
            boolean success, 
            String message, 
            Map<String, Object> additionalFields) {
        Map<String, Object> response = buildErrorResponse(success, message);
        if (additionalFields != null) {
            response.putAll(additionalFields);
        }
        return response;
    }

    /**
     * Builds a user-friendly error message from an exception message.
     * Maps technical error messages to user-friendly ones without exposing internal details.
     * 
     * @param exceptionMessage The exception message
     * @param defaultMessage Default message if no mapping found
     * @return User-friendly error message
     */
    public static String mapToUserFriendlyMessage(String exceptionMessage, String defaultMessage) {
        if (exceptionMessage == null) {
            return defaultMessage;
        }
        
        String lowerMessage = exceptionMessage.toLowerCase();
        
        if (lowerMessage.contains("timeout")) {
            return ApplicationConstants.REQUEST_TIMEOUT_ERROR;
        }
        
        if (lowerMessage.contains("connect") || lowerMessage.contains("broker")) {
            return ApplicationConstants.MQTT_CONNECTION_ERROR;
        }
        
        if (lowerMessage.contains("publish")) {
            return ApplicationConstants.MQTT_PUBLISH_ERROR;
        }
        
        if (lowerMessage.contains("device not found") || lowerMessage.contains("unknown")) {
            return ApplicationConstants.DEVICE_NOT_FOUND_ERROR;
        }
        
        return defaultMessage;
    }

    /**
     * Builds a ResponseEntity with a standardized error response.
     * 
     * @param success Whether the operation was successful
     * @param message Error message
     * @param statusCode HTTP status code
     * @return ResponseEntity with error response
     */
    public static ResponseEntity<Map<String, Object>> buildErrorResponseEntity(
            boolean success, 
            String message, 
            int statusCode) {
        Map<String, Object> response = buildErrorResponse(success, message);
        return ResponseEntity.status(statusCode).body(response);
    }
}
