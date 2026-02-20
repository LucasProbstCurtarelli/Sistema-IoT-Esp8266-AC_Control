package com.iot.app.controller;

import com.iot.app.dto.LightCommandRequest;
import com.iot.app.service.TuyaLightingService;
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
 * 
 * @author Sistema de Automação Residencial
 * @version 2.0
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
            @PathVariable String deviceName,
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
            logger.error("Error controlling light '{}': {}", deviceName, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            
            // Provide more specific error messages based on the exception
            String errorMessage = e.getMessage();
            if (errorMessage != null) {
                if (errorMessage.contains("connect") || errorMessage.contains("broker")) {
                    errorMessage = "Erro de conexão MQTT. Verifique se o broker Mosquitto está rodando (docker compose up).";
                } else if (errorMessage.contains("publish")) {
                    errorMessage = "Erro ao enviar comando MQTT. Verifique a conexão com o broker.";
                }
            } else {
                errorMessage = "Erro ao controlar lâmpada";
            }
            
            error.put("success", false);
            error.put("message", errorMessage);
            error.put("details", e.getMessage()); // Include original message for debugging
            return ResponseEntity.internalServerError().body(error);
            
        } catch (Exception e) {
            logger.error("Unexpected error controlling light '{}': {}", deviceName, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Erro inesperado ao controlar lâmpada: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
