package com.iot.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.app.service.entrance.EntrancePresenceListener;
import com.iot.app.service.entrance.EntrancePresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.BiConsumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EntrancePresenceListenerTest {

    @Mock
    private MqttService mqttService;

    @Mock
    private EntrancePresenceService presenceService;

    private EntrancePresenceListener listener;

    @BeforeEach
    void setUp() {
        listener = new EntrancePresenceListener(mqttService, presenceService, new ObjectMapper(), Runnable::run);
    }

    @Test
    void presenceFactsDoNotPublishTheAirConditionerTopic() {
        listener.afterPropertiesSet();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<BiConsumer<String, String>> handler = ArgumentCaptor.forClass(BiConsumer.class);
        verify(mqttService).subscribe(eq("/iot/entrada/presenca"), eq(0), handler.capture());

        handler.getValue().accept("/iot/entrada/presenca", "{\"present\":true,\"uid\":\"04A1B2C3\",\"lux\":180}");
        handler.getValue().accept("/iot/entrada/presenca", "{\"present\":false,\"lux\":800}");
        handler.getValue().accept("/iot/entrada/presenca", "{\"present\":true,\"uid\":\"04A1B2C3\"}");

        verify(presenceService).onReading(true, "04A1B2C3", 180);
        verify(presenceService).onReading(false, null, 800);
        verify(presenceService).onReading(true, "04A1B2C3", null);
        verify(mqttService, never()).publish(anyString(), anyString());
        verify(mqttService, never()).publish(eq("/iot/sensores/ac/comando"), anyString());
    }

    @Test
    void unreadablePayloadIsIgnored() {
        listener.handle("not-json");
        listener.handle("{\"present\":true}");
        listener.handle("{\"lux\":10}");

        verifyNoInteractions(presenceService);
    }
}
