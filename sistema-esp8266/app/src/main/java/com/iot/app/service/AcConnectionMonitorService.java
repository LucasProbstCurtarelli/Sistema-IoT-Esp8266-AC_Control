package com.iot.app.service;

import com.iot.app.config.MqttConfig;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service to monitor the connection status of the ESP8266 AC device.
 * 
 * This service maintains a persistent MQTT connection and subscribes to the
 * ESP8266 state topic to track when the device is online/offline.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
@Service
public class AcConnectionMonitorService implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(AcConnectionMonitorService.class);
    
    private static final String STATE_TOPIC = "/iot/sensores/ac/estado";
    private static final long OFFLINE_THRESHOLD_MS = 60000; // 60 seconds - if no message in this time, consider offline
    private static final int CONNECTION_TIMEOUT = 10;
    private static final int KEEP_ALIVE_INTERVAL = 60;
    
    private final MqttConfig mqttConfig;
    private final ReentrantLock lock = new ReentrantLock();
    
    private MqttClient mqttClient;
    private final AtomicLong lastMessageTimestamp = new AtomicLong(0);

    public AcConnectionMonitorService(MqttConfig mqttConfig) {
        this.mqttConfig = mqttConfig;
    }

    @Override
    public void afterPropertiesSet() {
        connect();
    }

    @Override
    public void destroy() {
        disconnect();
    }

    /**
     * Checks if the ESP8266 device is currently online.
     * 
     * A device is considered online if we've received a message from it
     * within the last OFFLINE_THRESHOLD_MS milliseconds.
     * 
     * If no messages have been received yet, the device is considered offline
     * until we receive at least one message from it.
     * 
     * @return true if device is online, false otherwise
     */
    public boolean isDeviceOnline() {
        long lastMessage = lastMessageTimestamp.get();
        if (lastMessage == 0) {
            // Never received a message - device is offline
            logger.debug("Device is offline: no messages received yet");
            return false;
        }
        
        long timeSinceLastMessage = System.currentTimeMillis() - lastMessage;
        boolean isOnline = timeSinceLastMessage < OFFLINE_THRESHOLD_MS;
        
        if (isOnline) {
            logger.debug("Device is online: last message {} ms ago", timeSinceLastMessage);
        } else {
            logger.debug("Device is offline: last message {} ms ago (threshold: {} ms)", 
                    timeSinceLastMessage, OFFLINE_THRESHOLD_MS);
        }
        
        return isOnline;
    }

    /**
     * Gets the timestamp of the last message received from the ESP8266.
     * 
     * @return timestamp in milliseconds, or 0 if no message received
     */
    public long getLastMessageTimestamp() {
        return lastMessageTimestamp.get();
    }

    /**
     * Connects to the MQTT broker and subscribes to the state topic.
     */
    private void connect() {
        lock.lock();
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                logger.debug("Already connected to MQTT broker for AC monitoring");
                return;
            }

            String clientId = "AcConnectionMonitor-" + System.currentTimeMillis();
            mqttClient = new MqttClient(mqttConfig.getBrokerUrl(), clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(CONNECTION_TIMEOUT);
            options.setKeepAliveInterval(KEEP_ALIVE_INTERVAL);
            options.setAutomaticReconnect(false);

            logger.info("Connecting to MQTT broker for AC connection monitoring: {}", mqttConfig.getBrokerUrl());
            mqttClient.connect(options);

            // Subscribe to state topic
            mqttClient.subscribe(STATE_TOPIC, 0, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    handleStateMessage(topic, message);
                }
            });

            logger.info("Subscribed to AC state topic: {}", STATE_TOPIC);
            
        } catch (MqttException e) {
            logger.error("Failed to connect to MQTT broker for AC monitoring: {}", e.getMessage(), e);
            mqttClient = null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles incoming messages from the ESP8266 state topic.
     */
    private void handleStateMessage(String topic, MqttMessage message) {
        long timestamp = System.currentTimeMillis();
        lastMessageTimestamp.set(timestamp);
        String payload = new String(message.getPayload());
        logger.info("Received message from ESP8266 on topic {} at {}: {}", topic, timestamp, payload);
    }

    /**
     * Disconnects from the MQTT broker.
     */
    private void disconnect() {
        lock.lock();
        try {
            if (mqttClient != null) {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect();
                }
                mqttClient.close();
                mqttClient = null;
                logger.info("Disconnected from MQTT broker for AC monitoring");
            }
        } catch (MqttException e) {
            logger.warn("Error disconnecting from MQTT broker: {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

}
