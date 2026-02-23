package com.iot.app.controller;

import com.iot.app.config.AcConfig;
import com.iot.app.constants.ApplicationConstants;
import com.iot.app.dto.AcPayload;
import com.iot.app.service.AcConnectionMonitorService;
import com.iot.app.service.IMqttService;
import com.iot.app.util.ErrorResponseBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for managing Air Conditioner via ESP8266.
 * 
 * This controller exposes HTTP endpoints to control the air conditioner
 * through the web dashboard. Commands are sent via MQTT to ESP8266.
 * 
 * Available endpoint:
 * - POST /api/ac - Sends control command (power, temperature)
 * 
 * Refactored to:
 * - Use dependency injection for service interface (Dependency Inversion)
 * - Use proper logging
 * - Extract constants to configuration class
 * 
 * @author Sistema de Automação Residencial
 * @version 2.0
 */
@RestController
@RequestMapping("/api/ac")
public class AcController {

    private static final Logger logger = LoggerFactory.getLogger(AcController.class);
    
    private final IMqttService mqttService;
    private final ObjectMapper objectMapper;
    private final AcConnectionMonitorService connectionMonitorService;

    public AcController(IMqttService mqttService, ObjectMapper objectMapper, AcConnectionMonitorService connectionMonitorService) {
        this.mqttService = mqttService;
        this.objectMapper = objectMapper;
        this.connectionMonitorService = connectionMonitorService;
    }

    /**
     * Endpoint to send commands to the air conditioner.
     * 
     * Receives a JSON object with parameters (power: "on"/"off", temp: temperature)
     * and publishes to MQTT topic for ESP8266 to process.
     * 
     * @param payload JSON object containing power and temperature
     * @return HTTP response indicating success or error
     */
    @PostMapping
    public ResponseEntity<?> sendCommand(@Valid @RequestBody AcPayload payload) {
        try {
            String jsonString = objectMapper.writeValueAsString(payload);
            logger.info("Sending AC command: {}", jsonString);
            
            mqttService.publish(AcConfig.TOPIC_CMD, jsonString);

            return ResponseEntity.ok(ApplicationConstants.AC_COMMAND_SUCCESS);
            
        } catch (Exception e) {
            logger.error("Error sending AC command: {}", e.getMessage(), e);
            String userMessage = ErrorResponseBuilder.mapToUserFriendlyMessage(
                e.getMessage(), 
                ApplicationConstants.DEVICE_CONTROL_ERROR
            );
            return ErrorResponseBuilder.buildErrorResponseEntity(false, userMessage, 500);
        }
    }

    /**
     * Endpoint to check the connection status of the ESP8266 AC device.
     * 
     * @return HTTP response with connection status information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean isOnline = connectionMonitorService.isDeviceOnline();
        long lastMessageTimestamp = connectionMonitorService.getLastMessageTimestamp();
        long lastMessageSecondsAgo = lastMessageTimestamp > 0 
            ? (System.currentTimeMillis() - lastMessageTimestamp) / 1000 
            : -1;
        
        Map<String, Object> response = new HashMap<>();
        response.put("connected", isOnline);
        response.put("lastMessageTimestamp", lastMessageTimestamp);
        response.put("lastMessageSecondsAgo", lastMessageSecondsAgo);
        
        logger.debug("AC status check: connected={}, lastMessageSecondsAgo={}", isOnline, lastMessageSecondsAgo);
        
        return ResponseEntity.ok(response);
    }
}