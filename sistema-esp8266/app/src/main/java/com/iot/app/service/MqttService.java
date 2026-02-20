package com.iot.app.service;

import com.iot.app.config.MqttConfig;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for MQTT communication with the broker.
 * 
 * This service publishes MQTT messages to Node-RED, which acts as an intermediary
 * between the Spring Boot application and IoT devices (ESP8266).
 * 
 * Refactored to:
 * - Use dependency injection for configuration (Dependency Inversion)
 * - Use proper logging instead of System.out.println
 * - Follow Single Responsibility Principle
 * 
 * @author Sistema de Automação Residencial
 * @version 2.0
 */
@Service
public class MqttService implements IMqttService {

    private static final Logger logger = LoggerFactory.getLogger(MqttService.class);
    
    private final MqttConfig mqttConfig;

    public MqttService(MqttConfig mqttConfig) {
        this.mqttConfig = mqttConfig;
    }

    /**
     * Publishes a message to a specific MQTT topic.
     * 
     * This method creates a temporary connection to the broker, publishes the message,
     * and disconnects immediately after. For high message frequency,
     * consider maintaining an open connection.
     * 
     * @param topic   MQTT topic where the message will be published
     * @param content Message content (typically JSON serialized)
     * @throws RuntimeException if there's an error in MQTT communication
     */
    @Override
    public void publish(String topic, String content) {
        MqttClient client = null;
        
        try {
            MemoryPersistence persistence = new MemoryPersistence();
            client = new MqttClient(mqttConfig.getBrokerUrl(), mqttConfig.getClientId(), persistence);
            
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            
            logger.debug("Connecting to MQTT broker: {}", mqttConfig.getBrokerUrl());
            client.connect(connOpts);
            
            MqttMessage message = new MqttMessage(content.getBytes());
            message.setQos(mqttConfig.getQos());
            
            logger.info("Publishing to topic '{}': {}", topic, content);
            client.publish(topic, message);
            
        } catch (MqttException me) {
            logger.error("Error publishing MQTT message: {}", me.getMessage(), me);
            throw new RuntimeException("Error in MQTT communication: " + me.getMessage(), me);
        } finally {
            safeDisconnect(client);
        }
    }
    
    /**
     * Safely disconnects and closes the MQTT client, ignoring errors.
     * 
     * @param client MQTT client to disconnect
     */
    private void safeDisconnect(MqttClient client) {
        if (client == null) {
            return;
        }
        
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException e) {
            logger.warn("Error disconnecting MQTT client: {}", e.getMessage());
        }
    }
}