package com.iot.app.service.entrance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.app.constants.ApplicationConstants;
import com.iot.app.service.MqttService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
public class EntrancePresenceListener implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(EntrancePresenceListener.class);

    private final MqttService mqttService;
    private final EntrancePresenceService presenceService;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public EntrancePresenceListener(
            MqttService mqttService,
            EntrancePresenceService presenceService,
            ObjectMapper objectMapper,
            @Qualifier("entradaPresenceExecutor") Executor executor) {
        this.mqttService = mqttService;
        this.presenceService = presenceService;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    @Override
    public void afterPropertiesSet() {
        mqttService.subscribe(ApplicationConstants.ENTRANCE_PRESENCE_TOPIC, 0, (topic, payload) ->
                executor.execute(() -> handle(payload)));
    }

    public void handle(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (!node.has("present") || !node.get("present").isBoolean()) {
                return;
            }
            boolean present = node.get("present").asBoolean();
            Integer lux = readLux(node);
            if (!present) {
                presenceService.onReading(false, null, lux);
                return;
            }
            if (!node.hasNonNull("uid")) {
                return;
            }
            presenceService.onReading(true, node.get("uid").asText(), lux);
        } catch (Exception ex) {
            logger.warn("Payload de presenca ignorado");
        }
    }

    private static Integer readLux(JsonNode node) {
        if (!node.has("lux") || node.get("lux").isNull()) {
            return null;
        }
        JsonNode lux = node.get("lux");
        if (!lux.isIntegralNumber()) {
            return null;
        }
        return lux.asInt();
    }
}
