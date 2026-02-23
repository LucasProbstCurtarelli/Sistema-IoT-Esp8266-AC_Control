package com.iot.app.constants;

/**
 * Application-wide constants.
 * 
 * Centralizes magic numbers and strings to improve maintainability
 * and follow DRY (Don't Repeat Yourself) principle.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
public final class ApplicationConstants {

    private ApplicationConstants() {
        // Utility class - prevent instantiation
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ==================== Time Constants (in seconds) ====================
    
    /** 24 hours in seconds (86400) */
    public static final int COOKIE_MAX_AGE_SECONDS = 86400;
    
    /** 1 hour in seconds (3600) */
    public static final int CORS_MAX_AGE_SECONDS = 3600;
    
    /** 1 year in seconds (31536000) - used for HSTS */
    public static final int HSTS_MAX_AGE_SECONDS = 31536000;
    
    // ==================== Time Constants (in milliseconds) ====================
    
    /** 24 hours in milliseconds (86400000) - default JWT expiration */
    public static final long JWT_EXPIRATION_MS = 86400000L;
    
    // ==================== Security Constants ====================
    
    /** JWT token cookie name */
    public static final String AUTH_TOKEN_COOKIE_NAME = "authToken";
    
    /** Authorization header Bearer prefix */
    public static final String BEARER_PREFIX = "Bearer ";
    
    /** Email domain suffix for user emails */
    public static final String USER_EMAIL_DOMAIN = "@automacao.com";
    
    // ==================== Rate Limiting Constants ====================
    
    /** Login endpoint rate limit: requests per window */
    public static final int LOGIN_RATE_LIMIT_REQUESTS = 5;
    
    /** Login endpoint rate limit: window duration in minutes */
    public static final int LOGIN_RATE_LIMIT_WINDOW_MINUTES = 15;
    
    /** API endpoint rate limit: requests per window */
    public static final int API_RATE_LIMIT_REQUESTS = 100;
    
    /** API endpoint rate limit: window duration in minutes */
    public static final int API_RATE_LIMIT_WINDOW_MINUTES = 1;
    
    // ==================== Token Blacklist Constants ====================
    
    /** Maximum size of token blacklist before clearing (10000) */
    public static final int TOKEN_BLACKLIST_MAX_SIZE = 10000;
    
    // ==================== MQTT Constants ====================
    
    /** MQTT topic prefix for Tuya devices */
    public static final String TUYA_TOPIC_PREFIX = "tuya/";
    
    /** MQTT topic suffix for commands */
    public static final String TUYA_COMMAND_SUFFIX = "/command";
    
    /** MQTT topic suffix for queries */
    public static final String TUYA_QUERY_SUFFIX = "/query";
    
    /** MQTT topic suffix for state */
    public static final String TUYA_STATE_SUFFIX = "/state";
    
    /** MQTT state topic pattern (wildcard) */
    public static final String TUYA_STATE_TOPIC_PATTERN = "tuya/+/state";
    
    // ==================== Response Messages ====================
    
    /** Success message for AC command */
    public static final String AC_COMMAND_SUCCESS = "Command sent successfully to air conditioner!";
    
    /** Success message for light command */
    public static final String LIGHT_COMMAND_SUCCESS = "Command sent successfully";
    
    /** Generic error message for device control */
    public static final String DEVICE_CONTROL_ERROR = "Unable to control device. Please try again later.";
    
    /** Error message for device status retrieval */
    public static final String DEVICE_STATUS_ERROR = "Unable to retrieve device status. Please try again later.";
    
    /** Error message for MQTT connection issues */
    public static final String MQTT_CONNECTION_ERROR = "MQTT connection error. Please verify the Mosquitto broker is running.";
    
    /** Error message for MQTT publish failures */
    public static final String MQTT_PUBLISH_ERROR = "Failed to send MQTT command. Please check the broker connection.";
    
    /** Error message for device not found */
    public static final String DEVICE_NOT_FOUND_ERROR = "Device not found or unavailable.";
    
    /** Error message for request timeout */
    public static final String REQUEST_TIMEOUT_ERROR = "Request timeout. Please verify the bridge is running.";
    
    /** Error message for unexpected errors */
    public static final String UNEXPECTED_ERROR = "An unexpected error occurred.";
    
    /** Error message for rate limit exceeded */
    public static final String RATE_LIMIT_EXCEEDED = "Rate limit exceeded. Please try again later.";
    
    // ==================== Validation Messages ====================
    
    /** Error message when no parameters provided */
    public static final String NO_PARAMETERS_PROVIDED = "At least one parameter must be provided (state, brightness, or color)";
}
