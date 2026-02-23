package com.iot.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iot.app.constants.ApplicationConstants;
import com.iot.app.dto.LightCommandRequest;
import com.iot.app.dto.LightStatusResponse;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for controlling Tuya-based Elgin smart bulbs via MQTT bridge.
 * 
 * This service maintains a persistent, thread-safe MQTT connection to the local broker
 * and provides methods to control smart bulbs through the tuya-mqtt bridge.
 * 
 * Features:
 * - Thread-safe connection management
 * - Automatic reconnection on broker failures
 * - Graceful shutdown handling
 * - Robust error handling with proper logging
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
@Service
public class TuyaLightingService implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(TuyaLightingService.class);
    
    private static final String CLIENT_ID_PREFIX = "TuyaLightingService-";
    private static final int QOS = 1;
    private static final int CONNECTION_TIMEOUT = 30;
    private static final int KEEP_ALIVE_INTERVAL = 60;
    private static final long RECONNECT_DELAY_MS = 5000;
    private static final long STATE_QUERY_TIMEOUT_SECONDS = 5;
    
    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;
    
    @Value("${mqtt.username:}")
    private String mqttUsername;
    
    @Value("${mqtt.password:}")
    private String mqttPassword;
    
    private final ObjectMapper objectMapper;
    private final ReentrantLock connectionLock = new ReentrantLock();
    
    private MqttClient mqttClient;
    private volatile boolean isShuttingDown = false;
    private Thread reconnectThread;
    private volatile boolean shouldReconnect = true;
    
    // Map para armazenar futures de consulta de estado (correlationId -> CompletableFuture)
    private final ConcurrentHashMap<String, CompletableFuture<LightStatusResponse>> pendingQueries = new ConcurrentHashMap<>();

    public TuyaLightingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterPropertiesSet() {
        connect();
        subscribeToStateTopic();
        startReconnectMonitor();
    }

    @Override
    public void destroy() {
        logger.info("Shutting down TuyaLightingService...");
        isShuttingDown = true;
        shouldReconnect = false;
        
        if (reconnectThread != null && reconnectThread.isAlive()) {
            reconnectThread.interrupt();
        }
        
        disconnect();
        logger.info("TuyaLightingService shutdown complete");
    }

    /**
     * Sends a light command with optional state, brightness, and color.
     * 
     * This method builds a dynamic MQTT payload using Tuya DPS format (Data Points).
     * Format discovered through testing with Elgin A70 lamp:
     * - DPS 20: Power (true/false)
     * - DPS 21: Mode ("colour" or "white")
     * - DPS 22: Brightness (10-1000)
     * - DPS 24: Color in hexadecimal format (HHHHSSSSVVVV)
     * 
     * @param deviceName The device name (e.g., "lampada_1", "lampada_2")
     * @param request DTO containing optional state, brightness, and color
     * @throws TuyaLightingException if the command cannot be sent
     * @throws IllegalArgumentException if deviceName is invalid or all fields are null
     */
    public void sendLightCommand(String deviceName, LightCommandRequest request) {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Device name cannot be null or empty");
        }
        
        if (request == null) {
            throw new IllegalArgumentException("Light command request cannot be null");
        }
        
        // Validate that at least one field is provided
        if (request.getState() == null && request.getBrightness() == null && request.getColor() == null) {
            throw new IllegalArgumentException("At least one field must be provided (state, brightness, or color)");
        }
        
        String topic = ApplicationConstants.TUYA_TOPIC_PREFIX + deviceName + ApplicationConstants.TUYA_COMMAND_SUFFIX;
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode dpsNode = objectMapper.createObjectNode();
        boolean hasColor = request.getColor() != null;
        boolean hasChanges = false;
        
        // IMPORTANT: Order matters for some Tuya devices
        // Always set mode (DPS 21) FIRST when color is involved, then color (DPS 24), then brightness (DPS 22)
        
        // DPS 20: Power state
        if (request.getState() != null) {
            dpsNode.put("20", request.getState());
            hasChanges = true;
            logger.debug("Adding power state: {}", request.getState());
        }
        
        // DPS 21: Mode - Set to "colour" when color OR brightness is provided
        // This ensures the lamp stays in colour mode and doesn't revert to white
        boolean hasBrightness = request.getBrightness() != null;
        if (hasColor || hasBrightness) {
            dpsNode.put("21", "colour");
            hasChanges = true;
            logger.debug("Setting mode to 'colour' to maintain color mode");
        }
        
        // DPS 24: Color in hexadecimal format (HHHHSSSSVVVV)
        // Set AFTER mode to ensure colour mode is active
        if (hasColor) {
            String tuyaColorHex = hexToTuyaColorHex(request.getColor());
            dpsNode.put("24", tuyaColorHex);
            hasChanges = true;
            logger.debug("Converted color {} to Tuya hex: {}", request.getColor(), tuyaColorHex);
        }
        
        // DPS 22: Brightness (convert from 0-100 to 10-1000)
        // Set AFTER mode and color to ensure they're applied first
        if (hasBrightness) {
            int brightnessPercent = request.getBrightness();
            // Convert from 0-100% to 10-1000 scale (Tuya range)
            int tuyaBrightness = Math.round((brightnessPercent / 100.0f) * 990) + 10;
            // Ensure it's within valid range
            tuyaBrightness = Math.max(10, Math.min(1000, tuyaBrightness));
            dpsNode.put("22", tuyaBrightness);
            hasChanges = true;
            logger.debug("Converted brightness from {}% to Tuya value {}", brightnessPercent, tuyaBrightness);
        }
        
        if (!hasChanges) {
            throw new IllegalArgumentException("No valid fields to send");
        }
        
        // Always use "dps" format - the bridge handles multiple DPS atomically
        payload.set("dps", dpsNode);
        
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
            logger.info("Sending light command to device '{}': {}", deviceName, jsonPayload);
        } catch (Exception e) {
            logger.error("Failed to serialize payload for device {}: {}", deviceName, e.getMessage(), e);
            throw new TuyaLightingException("Failed to serialize command payload", e);
        }
        
        publishMessage(topic, jsonPayload);
        logger.info("Light command sent successfully to device '{}'", deviceName);
    }
    
    /**
     * Sets the state of a light bulb (on/off).
     * 
     * This method is maintained for backward compatibility.
     * Internally calls sendLightCommand with only the state field.
     * 
     * @param deviceName The device name (e.g., "lampada_1", "lampada_2")
     * @param isOn True to turn on, false to turn off
     * @throws TuyaLightingException if the command cannot be sent
     */
    public void setLightState(String deviceName, boolean isOn) {
        LightCommandRequest request = new LightCommandRequest();
        request.setState(isOn);
        sendLightCommand(deviceName, request);
    }
    
    /**
     * Gets the real-time state of a light bulb by querying the device via MQTT.
     * 
     * This method uses a request/response pattern:
     * 1. Publishes a query message to tuya/{deviceName}/query with a correlation ID
     * 2. Waits for a response on tuya/{deviceName}/state with matching correlation ID
     * 3. Returns the device state or throws an exception on timeout/error
     * 
     * @param deviceName The device name (e.g., "lampada_1", "lampada_2")
     * @return LightStatusResponse with the current device state
     * @throws TuyaLightingException if the query fails or times out
     */
    public LightStatusResponse getRealState(String deviceName) {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Device name cannot be null or empty");
        }
        
        ensureConnected();
        
        // Generate unique correlation ID
        String correlationId = UUID.randomUUID().toString();
        
        // Create CompletableFuture for async response
        CompletableFuture<LightStatusResponse> future = new CompletableFuture<>();
        pendingQueries.put(correlationId, future);
        
        try {
            // Build query message
            ObjectNode queryPayload = objectMapper.createObjectNode();
            queryPayload.put("correlationId", correlationId);
            queryPayload.put("timestamp", System.currentTimeMillis());
            
            String queryTopic = ApplicationConstants.TUYA_TOPIC_PREFIX + deviceName + ApplicationConstants.TUYA_QUERY_SUFFIX;
            String jsonPayload;
            try {
                jsonPayload = objectMapper.writeValueAsString(queryPayload);
            } catch (JsonProcessingException e) {
                throw new TuyaLightingException("Failed to serialize query payload", e);
            }
            
            logger.debug("Publishing state query for device '{}' with correlation ID: {}", deviceName, correlationId);
            
            // Publish query
            publishMessage(queryTopic, jsonPayload);
            
            // Wait for response with timeout
            try {
                LightStatusResponse response = future.get(STATE_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                logger.debug("Received state response for device '{}'", deviceName);
                return response;
            } catch (TimeoutException e) {
                logger.error("Timeout waiting for state response from device '{}'", deviceName);
                throw new TuyaLightingException("Timeout waiting for device state response");
            } catch (Exception e) {
                logger.error("Error waiting for state response from device '{}': {}", deviceName, e.getMessage());
                throw new TuyaLightingException("Error getting device state: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            pendingQueries.remove(correlationId);
            if (e instanceof TuyaLightingException) {
                throw e;
            }
            throw new TuyaLightingException("Failed to query device state: " + e.getMessage(), e);
        } finally {
            // Clean up future if still pending
            pendingQueries.remove(correlationId);
        }
    }
    
    /**
     * Subscribes to state topic to receive responses to state queries.
     */
    private void subscribeToStateTopic() {
        connectionLock.lock();
        try {
            if (mqttClient == null || !mqttClient.isConnected()) {
                logger.warn("Cannot subscribe to state topic: MQTT client not connected");
                return;
            }
            
            String stateTopic = ApplicationConstants.TUYA_STATE_TOPIC_PATTERN;
            mqttClient.subscribe(stateTopic, QOS, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    handleStateMessage(topic, message);
                }
            });
            
            logger.info("Subscribed to state topic: {}", stateTopic);
        } catch (MqttException e) {
            logger.error("Failed to subscribe to state topic: {}", e.getMessage(), e);
        } finally {
            connectionLock.unlock();
        }
    }
    
    /**
     * Handles incoming state messages from the bridge.
     */
    private void handleStateMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            JsonNode jsonNode = objectMapper.readTree(payload);
            
            String correlationId = jsonNode.has("correlationId") ? 
                jsonNode.get("correlationId").asText() : null;
            
            if (correlationId == null) {
                logger.debug("Received state message without correlation ID, ignoring");
                return;
            }
            
            CompletableFuture<LightStatusResponse> future = pendingQueries.get(correlationId);
            if (future == null) {
                logger.debug("Received state message with unknown correlation ID: {}", correlationId);
                return;
            }
            
            // Check for error in response
            if (jsonNode.has("error")) {
                String error = jsonNode.get("error").asText();
                logger.error("Bridge returned error for correlation ID {}: {}", correlationId, error);
                future.completeExceptionally(new TuyaLightingException("Device error: " + error));
                pendingQueries.remove(correlationId);
                return;
            }
            
            // Extract device name from topic (tuya/lampada_1/state -> lampada_1)
            String deviceName = topic.split("/")[1];
            
            // Build LightStatusResponse from bridge response
            LightStatusResponse response = LightStatusResponse.builder()
                .success(true)
                .device(deviceName)
                .state(jsonNode.has("state") ? jsonNode.get("state").asBoolean() : null)
                .brightness(jsonNode.has("brightness") ? jsonNode.get("brightness").asInt() : null)
                .color(jsonNode.has("color") ? jsonNode.get("color").asText() : null)
                .lastUpdated(Instant.now())
                .build();
            
            future.complete(response);
            pendingQueries.remove(correlationId);
            
            logger.debug("Completed state query for device '{}' with correlation ID: {}", deviceName, correlationId);
            
        } catch (Exception e) {
            logger.error("Error handling state message: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Converts a hexadecimal color string (#RRGGBB) to Tuya color format (HHHHSSSSVVVV).
     * 
     * Tuya format uses HSV (Hue, Saturation, Value) encoded as 12-character hexadecimal:
     * - H: 0-360 (4 hex digits: 0x0000 to 0x0168)
     * - S: 0-1000 (4 hex digits: 0x0000 to 0x03E8)
     * - V: 0-1000 (4 hex digits: 0x0000 to 0x03E8)
     * 
     * @param hexColor Hexadecimal color string (e.g., "#FF0000" for red)
     * @return Tuya color hex string in format HHHHSSSSVVVV
     * @throws IllegalArgumentException if hex color format is invalid
     */
    private String hexToTuyaColorHex(String hexColor) {
        if (hexColor == null || !hexColor.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("Invalid hex color format. Expected format: #RRGGBB");
        }
        
        // Remove the '#' character
        String hex = hexColor.substring(1);
        
        // Parse RGB values (0-255)
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        
        // Convert RGB to HSV
        double[] hsv = rgbToHsv(r, g, b);
        
        // Convert HSV to Tuya format
        int hue = (int) Math.round(hsv[0]);           // 0-360
        int saturation = (int) Math.round(hsv[1]);    // 0-1000
        int value = (int) Math.round(hsv[2]);         // 0-1000
        
        // Ensure values are in valid range
        hue = Math.max(0, Math.min(360, hue));
        saturation = Math.max(0, Math.min(1000, saturation));
        value = Math.max(0, Math.min(1000, value));
        
        // Format as hexadecimal (4 digits each)
        String tuyaHex = String.format("%04x%04x%04x", hue, saturation, value);
        
        logger.debug("Converted hex color {} (RGB: r={}, g={}, b={}) to Tuya HSV: H={}, S={}, V={} -> {}", 
                hexColor, r, g, b, hue, saturation, value, tuyaHex);
        
        return tuyaHex;
    }
    
    /**
     * Converts RGB values (0-255) to HSV values.
     * 
     * @param r Red component (0-255)
     * @param g Green component (0-255)
     * @param b Blue component (0-255)
     * @return Array with [H, S, V] where H is 0-360, S and V are 0-1000
     */
    private double[] rgbToHsv(int r, int g, int b) {
        // Normalize RGB to 0-1
        double rNorm = r / 255.0;
        double gNorm = g / 255.0;
        double bNorm = b / 255.0;
        
        double max = Math.max(Math.max(rNorm, gNorm), bNorm);
        double min = Math.min(Math.min(rNorm, gNorm), bNorm);
        double delta = max - min;
        
        double h = 0;
        double s = 0;
        double v = max * 1000; // Value: 0-1000
        
        if (delta != 0) {
            // Saturation
            s = (delta / max) * 1000; // Saturation: 0-1000
            
            // Hue
            if (max == rNorm) {
                h = ((gNorm - bNorm) / delta) % 6;
            } else if (max == gNorm) {
                h = ((bNorm - rNorm) / delta) + 2;
            } else {
                h = ((rNorm - gNorm) / delta) + 4;
            }
            h = h * 60; // Convert to degrees (0-360)
            if (h < 0) {
                h += 360;
            }
        }
        
        return new double[]{h, s, v};
    }

    /**
     * Publishes a message to the MQTT broker.
     * 
     * @param topic The MQTT topic
     * @param payload The message payload
     * @throws TuyaLightingException if the message cannot be published
     */
    private void publishMessage(String topic, String payload) {
        ensureConnected();
        
        connectionLock.lock();
        try {
            if (mqttClient == null || !mqttClient.isConnected()) {
                throw new TuyaLightingException("MQTT client is not connected");
            }
            
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(QOS);
            message.setRetained(false);
            
            mqttClient.publish(topic, message);
            logger.debug("Published message to topic '{}': {}", topic, payload);
            
        } catch (MqttException e) {
            logger.error("Failed to publish message to topic '{}': {}", topic, e.getMessage(), e);
            handleConnectionError();
            throw new TuyaLightingException("Failed to publish MQTT message: " + e.getMessage(), e);
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Establishes connection to the MQTT broker.
     */
    private void connect() {
        connectionLock.lock();
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                logger.debug("Already connected to MQTT broker");
                return;
            }
            
            String clientId = CLIENT_ID_PREFIX + System.currentTimeMillis();
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(CONNECTION_TIMEOUT);
            options.setKeepAliveInterval(KEEP_ALIVE_INTERVAL);
            options.setAutomaticReconnect(false); // We handle reconnection manually
            
            // Set MQTT authentication if credentials are provided
            String username = mqttUsername;
            String password = mqttPassword;
            
            // Check environment variables if not set in properties
            if ((username == null || username.isEmpty()) && System.getenv("MQTT_USERNAME") != null) {
                username = System.getenv("MQTT_USERNAME");
            }
            if ((password == null || password.isEmpty()) && System.getenv("MQTT_PASSWORD") != null) {
                password = System.getenv("MQTT_PASSWORD");
            }
            
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                options.setUserName(username);
                options.setPassword(password.toCharArray());
                logger.debug("Using MQTT authentication with username: {}", username);
            } else {
                logger.warn("MQTT credentials not configured. Connection may fail if broker requires authentication.");
            }
            
            logger.info("Connecting to MQTT broker at {}...", brokerUrl);
            mqttClient.connect(options);
            
            logger.info("Successfully connected to MQTT broker");
            
            // Subscribe to state topic after connection
            subscribeToStateTopic();
            
        } catch (MqttException e) {
            logger.error("Failed to connect to MQTT broker: {}", e.getMessage(), e);
            mqttClient = null;
            throw new TuyaLightingException("Failed to connect to MQTT broker: " + e.getMessage(), e);
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Disconnects from the MQTT broker.
     */
    private void disconnect() {
        connectionLock.lock();
        try {
            if (mqttClient != null) {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect();
                    logger.info("Disconnected from MQTT broker");
                }
                mqttClient.close();
                mqttClient = null;
            }
        } catch (MqttException e) {
            logger.warn("Error during MQTT disconnection: {}", e.getMessage());
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Ensures the MQTT client is connected, attempting reconnection if necessary.
     */
    private void ensureConnected() {
        connectionLock.lock();
        try {
            if (mqttClient == null || !mqttClient.isConnected()) {
                logger.warn("MQTT client not connected, attempting reconnection...");
                connect();
            }
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Checks if the MQTT client is currently connected.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isMqttConnected() {
        connectionLock.lock();
        try {
            return mqttClient != null && mqttClient.isConnected();
        } catch (Exception e) {
            logger.debug("Error checking MQTT connection status: {}", e.getMessage());
            return false;
        } finally {
            connectionLock.unlock();
        }
    }
    
    /**
     * Handles connection errors and triggers reconnection if needed.
     */
    private void handleConnectionError() {
        connectionLock.lock();
        try {
            if (mqttClient != null) {
                try {
                    if (mqttClient.isConnected()) {
                        mqttClient.disconnect();
                    }
                } catch (MqttException e) {
                    logger.debug("Error disconnecting after connection error: {}", e.getMessage());
                }
                mqttClient = null;
            }
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Starts a background thread to monitor connection health and reconnect if needed.
     */
    private void startReconnectMonitor() {
        reconnectThread = new Thread(() -> {
            while (!isShuttingDown && shouldReconnect) {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                    
                    if (isShuttingDown || !shouldReconnect) {
                        break;
                    }
                    
                    connectionLock.lock();
                    try {
                        if (mqttClient == null || !mqttClient.isConnected()) {
                            logger.info("Connection lost, attempting to reconnect...");
                            connect();
                        }
                    } finally {
                        connectionLock.unlock();
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.debug("Reconnect monitor thread interrupted");
                    break;
                } catch (Exception e) {
                    logger.error("Error in reconnect monitor: {}", e.getMessage(), e);
                }
            }
            logger.debug("Reconnect monitor thread stopped");
        }, "TuyaLightingService-ReconnectMonitor");
        
        reconnectThread.setDaemon(true);
        reconnectThread.start();
        logger.debug("Reconnect monitor thread started");
    }

    /**
     * Custom exception for Tuya lighting service errors.
     */
    public static class TuyaLightingException extends RuntimeException {
        public TuyaLightingException(String message) {
            super(message);
        }

        public TuyaLightingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
