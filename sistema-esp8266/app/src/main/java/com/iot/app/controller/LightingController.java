package com.iot.app.controller;

import com.iot.app.constants.ApplicationConstants;
import com.iot.app.dto.LightCommandRequest;
import com.iot.app.dto.LightStatusResponse;
import com.iot.app.service.TuyaLightingService;
import com.iot.app.util.ErrorResponseBuilder;
import com.iot.app.util.SuccessResponseBuilder;
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
                return ErrorResponseBuilder.buildErrorResponseEntity(
                    false, 
                    ApplicationConstants.NO_PARAMETERS_PROVIDED, 
                    400
                );
            }

            logger.info("Sending light command for device '{}': state={}, brightness={}, color={}", 
                    deviceName, request.getState(), request.getBrightness(), request.getColor());
            
            lightingService.sendLightCommand(deviceName, request);

            Map<String, Object> additionalFields = new HashMap<>();
            additionalFields.put("device", deviceName);
            additionalFields.put("state", request.getState());
            additionalFields.put("brightness", request.getBrightness());
            additionalFields.put("color", request.getColor());
            
            return SuccessResponseBuilder.buildSuccessResponseEntity(
                ApplicationConstants.LIGHT_COMMAND_SUCCESS, 
                additionalFields
            );
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for device '{}': {}", deviceName, e.getMessage());
            return ErrorResponseBuilder.buildErrorResponseEntity(false, e.getMessage(), 400);
            
        } catch (TuyaLightingService.TuyaLightingException e) {
            // Log full error details server-side only
            logger.error("Error controlling light '{}': {}", deviceName, e.getMessage(), e);
            
            String errorMessage = ErrorResponseBuilder.mapToUserFriendlyMessage(
                e.getMessage(), 
                ApplicationConstants.DEVICE_CONTROL_ERROR
            );
            
            return ErrorResponseBuilder.buildErrorResponseEntity(false, errorMessage, 500);
            
        } catch (Exception e) {
            // Log full error details server-side only
            logger.error("Unexpected error controlling light '{}': {}", deviceName, e.getMessage(), e);
            return ErrorResponseBuilder.buildErrorResponseEntity(
                false, 
                ApplicationConstants.UNEXPECTED_ERROR + " while controlling the device.", 
                500
            );
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
            
            String errorMessage = ErrorResponseBuilder.mapToUserFriendlyMessage(
                e.getMessage(), 
                ApplicationConstants.DEVICE_STATUS_ERROR
            );
            
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
                    .message(ApplicationConstants.UNEXPECTED_ERROR + " while retrieving device status.")
                    .build();
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
