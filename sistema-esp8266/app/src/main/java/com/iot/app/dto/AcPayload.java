package com.iot.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO (Data Transfer Object) for Air Conditioner control commands.
 * 
 * This object is used to serialize/deserialize JSON commands sent
 * via MQTT to ESP8266.
 * 
 * Refactored to add validation annotations.
 * 
 * @author Sistema de Automação Residencial
 * @version 2.0
 */
@Data
public class AcPayload {
    
    /**
     * Power state of the air conditioner.
     * Accepted values: "on" or "off".
     */
    @NotBlank(message = "Power state is required")
    private String power;
    
    /**
     * Temperature setting.
     * Typical range: 16-30 degrees Celsius.
     */
    @Min(value = 16, message = "Temperature must be at least 16")
    @Max(value = 30, message = "Temperature must be at most 30")
    private int temp;
}