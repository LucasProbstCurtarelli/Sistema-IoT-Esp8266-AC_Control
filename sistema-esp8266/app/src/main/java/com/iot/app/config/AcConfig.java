package com.iot.app.config;

/**
 * Configuration class for AC device settings.
 * 
 * Centralizes AC device configuration constants.
 * Follows Single Responsibility Principle by separating configuration concerns.
 */
public class AcConfig {
    
    public static final String TOPIC_CMD = "/iot/sensores/ac/comando";
    
    private AcConfig() {
        // Utility class - prevent instantiation
    }
}
