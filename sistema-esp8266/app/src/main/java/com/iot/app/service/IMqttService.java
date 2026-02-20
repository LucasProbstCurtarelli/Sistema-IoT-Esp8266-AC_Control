package com.iot.app.service;

/**
 * Interface for MQTT service operations.
 * 
 * Follows Dependency Inversion Principle by defining an abstraction
 * that can be implemented in different ways (e.g., connection pooling, different brokers).
 */
public interface IMqttService {
    
    /**
     * Publishes a message to an MQTT topic.
     * 
     * @param topic   MQTT topic where the message will be published
     * @param content Message content (typically JSON serialized)
     * @throws RuntimeException if there's an error in MQTT communication
     */
    void publish(String topic, String content);
}
