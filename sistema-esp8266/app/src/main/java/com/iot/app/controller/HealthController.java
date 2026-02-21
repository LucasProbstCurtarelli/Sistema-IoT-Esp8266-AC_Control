package com.iot.app.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller for monitoring application status.
 * 
 * Provides endpoints to check the health and status of the application.
 * Useful for load balancers, monitoring systems, and deployment pipelines.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    /**
     * Basic health check endpoint.
     * 
     * Returns 200 OK if the application is running and responsive.
     * This endpoint is public and does not require authentication.
     * 
     * @return HTTP response with health status
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("service", "Sistema de Automação Residencial");
        
        logger.debug("Health check requested");
        return ResponseEntity.ok(health);
    }

    /**
     * Detailed health check endpoint with additional information.
     * 
     * Returns application status, version, and system information.
     * This endpoint is public and does not require authentication.
     * 
     * @return HTTP response with detailed health status
     */
    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("service", "Sistema de Automação Residencial");
        health.put("version", "1.0.0");
        
        // System information
        Map<String, Object> system = new HashMap<>();
        system.put("javaVersion", System.getProperty("java.version"));
        system.put("javaVendor", System.getProperty("java.vendor"));
        system.put("osName", System.getProperty("os.name"));
        system.put("osVersion", System.getProperty("os.version"));
        health.put("system", system);
        
        logger.debug("Detailed health check requested");
        return ResponseEntity.ok(health);
    }
}
