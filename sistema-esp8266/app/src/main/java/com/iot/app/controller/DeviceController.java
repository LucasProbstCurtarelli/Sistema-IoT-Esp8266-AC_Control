package com.iot.app.controller;

import com.iot.app.dto.DeviceResponse;
import com.iot.app.model.Device;
import com.iot.app.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for managing devices.
 * 
 * Provides endpoints to retrieve device information.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);
    
    private final DeviceRepository deviceRepository;

    public DeviceController(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    /**
     * Get all enabled devices.
     * 
     * @return List of all enabled devices
     */
    @GetMapping
    public ResponseEntity<List<DeviceResponse>> getAllDevices() {
        logger.debug("Fetching all enabled devices");
        
        List<Device> devices = deviceRepository.findByEnabledTrue();
        List<DeviceResponse> deviceResponses = devices.stream()
                .map(this::toDeviceResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(deviceResponses);
    }

    /**
     * Get device by device name.
     * 
     * @param deviceName The device name (e.g., "lampada_1")
     * @return Device information
     */
    @GetMapping("/{deviceName}")
    public ResponseEntity<DeviceResponse> getDevice(@PathVariable String deviceName) {
        logger.debug("Fetching device: {}", deviceName);
        
        return deviceRepository.findByDeviceName(deviceName)
                .map(device -> ResponseEntity.ok(toDeviceResponse(device)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Convert Device entity to DeviceResponse DTO.
     */
    private DeviceResponse toDeviceResponse(Device device) {
        return DeviceResponse.builder()
                .id(device.getId().toString())
                .deviceName(device.getDeviceName())
                .displayName(device.getDisplayName())
                .location(device.getLocation())
                .deviceType(device.getDeviceType())
                .enabled(device.getEnabled())
                .build();
    }
}
