package com.iot.app.service.entrance;

import com.iot.app.dto.LightCommandRequest;
import com.iot.app.dto.LightStatusResponse;
import com.iot.app.service.TuyaLightingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TuyaEntranceLampAdapter implements EntranceLampPort {

    private static final Logger logger = LoggerFactory.getLogger(TuyaEntranceLampAdapter.class);

    private final TuyaLightingService lighting;

    public TuyaEntranceLampAdapter(TuyaLightingService lighting) {
        this.lighting = lighting;
    }

    @Override
    public Optional<LampView> read(String deviceName) {
        try {
            Optional<LightStatusResponse> cached = lighting.latestKnownState(deviceName);
            if (cached.isPresent() && cached.get().getState() != null) {
                return Optional.of(toView(cached.get()));
            }
            LightStatusResponse live = lighting.getRealState(deviceName);
            if (live.getState() == null) {
                return Optional.empty();
            }
            return Optional.of(toView(live));
        } catch (RuntimeException ex) {
            logger.warn("Falha ao ler estado de {}", deviceName);
            return Optional.empty();
        }
    }

    @Override
    public boolean apply(String deviceName, LightCommandRequest request) {
        try {
            lighting.sendLightCommand(deviceName, request);
            return true;
        } catch (RuntimeException ex) {
            logger.warn("Falha ao comandar {}", deviceName);
            return false;
        }
    }

    private static LampView toView(LightStatusResponse response) {
        return new LampView(
                Boolean.TRUE.equals(response.getState()),
                response.getColor(),
                response.getBrightness());
    }
}
