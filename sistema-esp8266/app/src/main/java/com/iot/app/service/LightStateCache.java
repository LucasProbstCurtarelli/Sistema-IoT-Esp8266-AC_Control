package com.iot.app.service;

import com.iot.app.dto.LightCommandRequest;
import com.iot.app.dto.LightStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for caching light bulb states.
 * 
 * Since Tuya devices use a fire-and-forget protocol via MQTT,
 * we maintain an in-memory cache of the last known state.
 * 
 * This cache is updated whenever a command is sent successfully.
 * Note: The actual device state may differ if someone uses
 * the physical switch or another control method.
 */
@Service
public class LightStateCache {

    private static final Logger logger = LoggerFactory.getLogger(LightStateCache.class);
    
    /**
     * Internal state holder for a light.
     */
    private static class LightState {
        boolean power = false;
        int brightness = 100;
        String color = "#FFFFFF";
        Instant lastUpdated = Instant.now();
        
        LightStatusResponse toResponse(String deviceName) {
            return LightStatusResponse.builder()
                    .success(true)
                    .device(deviceName)
                    .state(power)
                    .brightness(brightness)
                    .color(color)
                    .lastUpdated(lastUpdated)
                    .build();
        }
    }
    
    private final Map<String, LightState> stateCache = new ConcurrentHashMap<>();
    
    /**
     * Updates the cached state based on a command.
     * 
     * @param deviceName The device name
     * @param request The command that was sent
     */
    public void updateState(String deviceName, LightCommandRequest request) {
        LightState state = stateCache.computeIfAbsent(deviceName, k -> new LightState());
        
        if (request.getState() != null) {
            state.power = request.getState();
        }
        
        if (request.getBrightness() != null) {
            state.brightness = request.getBrightness();
        }
        
        if (request.getColor() != null) {
            state.color = request.getColor();
        }
        
        state.lastUpdated = Instant.now();
        
        logger.debug("Updated cache for device '{}': power={}, brightness={}, color={}", 
                deviceName, state.power, state.brightness, state.color);
    }
    
    /**
     * Gets the cached state for a device.
     * 
     * @param deviceName The device name
     * @return The cached state, or a default state if not found
     */
    public LightStatusResponse getState(String deviceName) {
        LightState state = stateCache.get(deviceName);
        
        if (state == null) {
            logger.debug("No cached state for device '{}', returning defaults", deviceName);
            return LightStatusResponse.builder()
                    .success(true)
                    .device(deviceName)
                    .state(false)
                    .brightness(100)
                    .color("#FFFFFF")
                    .message("Estado padrão - dispositivo ainda não foi controlado")
                    .build();
        }
        
        return state.toResponse(deviceName);
    }
    
    /**
     * Checks if a device has cached state.
     * 
     * @param deviceName The device name
     * @return true if the device has cached state
     */
    public boolean hasState(String deviceName) {
        return stateCache.containsKey(deviceName);
    }
    
    /**
     * Clears the cached state for a device.
     * 
     * @param deviceName The device name
     */
    public void clearState(String deviceName) {
        stateCache.remove(deviceName);
        logger.debug("Cleared cache for device '{}'", deviceName);
    }
    
    /**
     * Clears all cached states.
     */
    public void clearAll() {
        stateCache.clear();
        logger.debug("Cleared all cached states");
    }
}
