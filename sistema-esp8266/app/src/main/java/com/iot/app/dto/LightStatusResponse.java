package com.iot.app.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * DTO for Light status response.
 * 
 * Returns the current known state of a light bulb.
 * Note: This represents the last known state, not necessarily
 * the actual device state (fire-and-forget architecture).
 */
@Data
@Builder
public class LightStatusResponse {
    
    /**
     * Whether the request was successful.
     */
    private boolean success;
    
    /**
     * The device name.
     */
    private String device;
    
    /**
     * Power state of the light (true = on, false = off).
     */
    private Boolean state;
    
    /**
     * Brightness level (0-100).
     */
    private Integer brightness;
    
    /**
     * Color in hexadecimal format (#RRGGBB).
     */
    private String color;
    
    /**
     * Timestamp of the last command sent to this device.
     */
    private Instant lastUpdated;
    
    /**
     * Optional message for additional context.
     */
    private String message;
}
