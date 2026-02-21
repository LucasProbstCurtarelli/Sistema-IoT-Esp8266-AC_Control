package com.iot.app.controller;

import com.iot.app.dto.LightCommandRequest;
import com.iot.app.dto.LightStatusResponse;
import com.iot.app.service.TuyaLightingService;
import com.iot.app.validation.ValidDeviceName;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for managing Tuya smart bulbs.
 * 
 * This controller exposes HTTP endpoints to control smart bulbs
 * through the web dashboard. Commands are sent via MQTT to the tuya-mqtt bridge.
 * 
 * Available endpoints:
 * - POST /api/lights/{deviceName} - Controls a light bulb (state, brightness, color)
 * - GET /api/lights/{deviceName}/status - Gets the current state of a light bulb
 * 
 * @author Sistema de Automação Residencial
 * @version 2.1
 */
@RestController
@RequestMapping("/api/lights")
public class LightingController {

    private static final Logger logger = LoggerFactory.getLogger(LightingController.class);
    
    private final TuyaLightingService lightingService;

    public LightingController(TuyaLightingService lightingService) {
        this.lightingService = lightingService;
    }

    /**
     * Endpoint to control a light bulb.
     * 
     * Receives a JSON object with optional parameters (state, brightness, color)
     * and sends the command to the specified device via MQTT.
     * 
     * @param deviceName The device name (e.g., "lampada_1", "lampada_2")
     * @param request DTO containing optional state, brightness, and color
     * @return HTTP response indicating success or error
     */
    @PostMapping("/{deviceName}")
    public ResponseEntity<Map<String, Object>> setLightState(
            @PathVariable @ValidDeviceName String deviceName,
            @Valid @RequestBody LightCommandRequest request) {
        try {
            // Validate that at least one field is provided
            if (request.getState() == null && request.getBrightness() == null && request.getColor() == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Pelo menos um parâmetro deve ser fornecido (state, brightness ou color)");
                return ResponseEntity.badRequest().body(error);
            }

            logger.info("Sending light command for device '{}': state={}, brightness={}, color={}", 
                    deviceName, request.getState(), request.getBrightness(), request.getColor());
            
            lightingService.sendLightCommand(deviceName, request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Comando enviado com sucesso");
            response.put("device", deviceName);
            response.put("state", request.getState());
            response.put("brightness", request.getBrightness());
            response.put("color", request.getColor());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for device '{}': {}", deviceName, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
            
        } catch (TuyaLightingService.TuyaLightingException e) {
            // Log full error details server-side only
            logger.error("Error controlling light '{}': {}", deviceName, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            
            // Provide user-friendly error messages without exposing internal details
            String errorMessage = "Unable to control device. Please try again later.";
            String userMessage = e.getMessage();
            if (userMessage != null) {
                if (userMessage.contains("connect") || userMessage.contains("broker")) {
                    errorMessage = "MQTT connection error. Please verify the Mosquitto broker is running.";
                } else if (userMessage.contains("publish")) {
                    errorMessage = "Failed to send MQTT command. Please check the broker connection.";
                } else if (userMessage.contains("Device not found") || userMessage.contains("unknown")) {
                    errorMessage = "Device not found or unavailable.";
                }
            }
            
            error.put("success", false);
            error.put("message", errorMessage);
            return ResponseEntity.internalServerError().body(error);
            
        } catch (Exception e) {
            // Log full error details server-side only
            logger.error("Unexpected error controlling light '{}': {}", deviceName, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "An unexpected error occurred while controlling the device.");
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Endpoint to get the current status of a light bulb.
     * 
     * Queries the actual device state via MQTT request/response pattern.
     * This returns the real-time state from the device, not a cached value.
     * 
     * @param deviceName The device name (e.g., "lampada_1", "lampada_2")
     * @return HTTP response with the light status
     */
    @GetMapping("/{deviceName}/status")
    public ResponseEntity<LightStatusResponse> getLightStatus(@PathVariable @ValidDeviceName String deviceName) {
        logger.debug("Getting real-time status for device '{}'", deviceName);
        
        try {
            LightStatusResponse status = lightingService.getRealState(deviceName);
            return ResponseEntity.ok(status);
            
        } catch (TuyaLightingService.TuyaLightingException e) {
            // Log full error details server-side only
            logger.error("Error getting status for device '{}': {}", deviceName, e.getMessage(), e);
            
            // Provide user-friendly error messages without exposing internal details
            String errorMessage = "Unable to retrieve device status. Please try again later.";
            String userMessage = e.getMessage();
            if (userMessage != null) {
                if (userMessage.contains("Timeout")) {
                    errorMessage = "Request timeout. Please verify the bridge is running.";
                } else if (userMessage.contains("connect") || userMessage.contains("broker")) {
                    errorMessage = "MQTT connection error. Please verify the Mosquitto broker is running.";
                } else if (userMessage.contains("Device not found") || userMessage.contains("unknown")) {
                    errorMessage = "Device not found or unavailable.";
                }
            }
            
            LightStatusResponse errorResponse = LightStatusResponse.builder()
                    .success(false)
                    .device(deviceName)
                    .message(errorMessage)
                    .build();
            
            return ResponseEntity.internalServerError().body(errorResponse);
            
        } catch (Exception e) {
            // Log full error details server-side only
            logger.error("Unexpected error getting status for device '{}': {}", deviceName, e.getMessage(), e);
            
            LightStatusResponse errorResponse = LightStatusResponse.builder()
                    .success(false)
                    .device(deviceName)
                    .message("An unexpected error occurred while retrieving device status.")
                    .build();
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
