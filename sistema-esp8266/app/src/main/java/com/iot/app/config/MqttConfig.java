package com.iot.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for MQTT settings.
 * 
 * Externalizes MQTT configuration to follow Dependency Inversion Principle.
 * Configuration values can be overridden via application.properties.
 */
@Configuration
public class MqttConfig {
    
    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;
    
    @Value("${mqtt.client.id:SpringAppServer}")
    private String clientId;
    
    @Value("${mqtt.qos:1}")
    private int qos;
    
    public String getBrokerUrl() {
        return brokerUrl;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public int getQos() {
        return qos;
    }
}
