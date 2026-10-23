package com.iot.app.service;

import com.iot.app.config.MqttConfig;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * Persistent MQTT publisher used by the air-conditioner controller.
 *
 * One client stays connected for the life of the process. A background thread
 * reconnects after the broker drops the session. {@link #publish(String, String)}
 * reuses that client and does not open a connection per message.
 */
@Service
public class MqttService implements IMqttService, InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(MqttService.class);

    private static final int CONNECTION_TIMEOUT_SECONDS = 10;
    private static final int KEEP_ALIVE_SECONDS = 60;
    private static final long RECONNECT_DELAY_MS = 5000;
    private static final String DEFAULT_CLIENT_ID = "SpringAppClient";

    private final MqttConfig mqttConfig;
    private final ReentrantLock connectionLock = new ReentrantLock();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    private MqttClient mqttClient;
    private volatile boolean shuttingDown = false;
    private Thread reconnectThread;

    public MqttService(MqttConfig mqttConfig) {
        this.mqttConfig = mqttConfig;
    }

    @Override
    public void afterPropertiesSet() {
        connectionLock.lock();
        try {
            connectLocked();
        } catch (RuntimeException e) {
            logger.warn("Initial MQTT connection failed; will retry: {}", e.getMessage());
        } finally {
            connectionLock.unlock();
        }
        startReconnectMonitor();
    }

    @Override
    public void destroy() {
        logger.info("Shutting down MqttService...");
        shuttingDown = true;
        if (reconnectThread != null) {
            reconnectThread.interrupt();
        }
        connectionLock.lock();
        try {
            closeClientLocked();
        } finally {
            connectionLock.unlock();
        }
        logger.info("MqttService shutdown complete");
    }

    /**
     * Subscribes on the shared client. The handler is invoked again after a reconnect.
     * Handlers must not assume they run while this service holds its connection lock.
     */
    public void subscribe(String topic, int qos, BiConsumer<String, String> handler) {
        if (topic == null || topic.isBlank() || handler == null) {
            throw new IllegalArgumentException("topic and handler are required");
        }
        subscriptions.add(new Subscription(topic, qos, handler));
        connectionLock.lock();
        try {
            if (isConnectedLocked()) {
                mqttClient.subscribe(topic, qos);
            }
        } catch (MqttException ex) {
            logger.error("Failed to subscribe to {}: {}", topic, ex.getMessage());
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Publishes on the shared client. The topic and payload are the caller's;
     * this method does not open or close a connection around the publish.
     */
    @Override
    public void publish(String topic, String content) {
        connectionLock.lock();
        try {
            ensureConnectedLocked();
            MqttMessage message = new MqttMessage(content.getBytes(StandardCharsets.UTF_8));
            message.setQos(mqttConfig.getQos());
            message.setRetained(false);
            logger.info("Publishing to topic '{}': {}", topic, content);
            mqttClient.publish(topic, message);
        } catch (MqttException me) {
            logger.error("Error publishing MQTT message: {}", me.getMessage(), me);
            closeClientLocked();
            throw new RuntimeException("Error in MQTT communication: " + me.getMessage(), me);
        } finally {
            connectionLock.unlock();
        }
    }

    private void ensureConnectedLocked() {
        if (shuttingDown) {
            throw new RuntimeException("Error in MQTT communication: client is shutting down");
        }
        if (isConnectedLocked()) {
            return;
        }
        logger.warn("MQTT client not connected, attempting reconnection...");
        connectLocked();
    }

    private void connectLocked() {
        if (shuttingDown) {
            throw new RuntimeException("Error in MQTT communication: client is shutting down");
        }
        if (isConnectedLocked()) {
            return;
        }
        closeClientLocked();

        String clientId = mqttConfig.getClientId();
        if (clientId == null || clientId.isEmpty()) {
            clientId = DEFAULT_CLIENT_ID;
        }

        try {
            mqttClient = new MqttClient(mqttConfig.getBrokerUrl(), clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(CONNECTION_TIMEOUT_SECONDS);
            options.setKeepAliveInterval(KEEP_ALIVE_SECONDS);
            options.setAutomaticReconnect(false);
            applyCredentials(options);

            mqttClient.setCallback(callback());
            logger.info("Connecting to MQTT broker at {}...", mqttConfig.getBrokerUrl());
            mqttClient.connect(options);
            resubscribeLocked();
            logger.info("MQTT client connected");
        } catch (MqttException e) {
            closeClientLocked();
            throw new RuntimeException(
                    "Error in MQTT communication: failed to connect to broker: " + e.getMessage(), e);
        }
    }

    private void applyCredentials(MqttConnectOptions options) {
        String username = mqttConfig.getUsername();
        String password = mqttConfig.getPassword();
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            logger.debug("MQTT credentials not configured; connecting without authentication");
            return;
        }
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        logger.debug("Using MQTT authentication with username: {}", username);
    }

    private boolean isConnectedLocked() {
        try {
            return mqttClient != null && mqttClient.isConnected();
        } catch (Exception e) {
            logger.debug("Error checking MQTT connection status: {}", e.getMessage());
            return false;
        }
    }

    private void closeClientLocked() {
        MqttClient client = mqttClient;
        mqttClient = null;
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnectForcibly(1000, 1000);
            }
            client.close();
        } catch (MqttException e) {
            logger.warn("Error closing MQTT client: {}", e.getMessage());
        }
    }

    private void resubscribeLocked() {
        for (Subscription subscription : subscriptions) {
            try {
                mqttClient.subscribe(subscription.topic, subscription.qos);
            } catch (MqttException ex) {
                logger.error("Failed to resubscribe to {}: {}", subscription.topic, ex.getMessage());
            }
        }
    }

    private MqttCallback callback() {
        return new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                logger.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                for (Subscription subscription : subscriptions) {
                    if (!subscription.topic.equals(topic)) {
                        continue;
                    }
                    try {
                        subscription.handler.accept(topic, payload);
                    } catch (RuntimeException ex) {
                        logger.error("MQTT handler failed for {}: {}", topic, ex.getMessage());
                    }
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // publish does not wait for completion
            }
        };
    }

    private void startReconnectMonitor() {
        reconnectThread = new Thread(() -> {
            while (!shuttingDown) {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (shuttingDown) {
                    break;
                }
                connectionLock.lock();
                try {
                    if (shuttingDown || isConnectedLocked()) {
                        continue;
                    }
                    logger.info("MQTT connection lost, attempting to reconnect...");
                    connectLocked();
                } catch (RuntimeException e) {
                    logger.warn("MQTT reconnect failed: {}", e.getMessage());
                } finally {
                    connectionLock.unlock();
                }
            }
            logger.debug("MQTT reconnect monitor stopped");
        }, "MqttService-ReconnectMonitor");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    private record Subscription(String topic, int qos, BiConsumer<String, String> handler) {
    }
}
