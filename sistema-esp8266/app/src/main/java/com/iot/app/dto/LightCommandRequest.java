package com.iot.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * DTO (Data Transfer Object) for Light control commands.
 * 
 * This object is used to serialize/deserialize JSON commands sent
 * via MQTT to Tuya smart bulbs through the tuya-mqtt bridge.
 * 
 * All fields are optional to allow partial updates (e.g., only brightness,
 * only color, or only state).
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
@Data
public class LightCommandRequest {
    
    /**
     * Power state of the light bulb.
     * True to turn on, false to turn off.
     * Optional - allows changing brightness/color without toggling state.
     */
    private Boolean state;
    
    /**
     * Brightness level of the light bulb.
     * Range: 0-100 (percentage).
     * Will be converted to Tuya DPS 22 scale (10-1000).
     * Optional - allows changing state/color without changing brightness.
     */
    @Min(value = 0, message = "Brightness must be at least 0")
    @Max(value = 100, message = "Brightness must be at most 100")
    private Integer brightness;
    
    /**
     * Color of the light bulb in hexadecimal format.
     * Format: "#RRGGBB" (e.g., "#FF0000" for red, "#0000FF" for blue).
     * Will be converted to Tuya DPS 24 format: HSV as hexadecimal (HHHHSSSSVVVV).
     * When color is provided, the mode (DPS 21) is automatically set to "colour".
     * Optional - allows changing state/brightness without changing color.
     */
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hexadecimal color (e.g., #FF0000)")
    private String color;
}
