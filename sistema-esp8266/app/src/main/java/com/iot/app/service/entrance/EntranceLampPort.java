package com.iot.app.service.entrance;

import com.iot.app.dto.LightCommandRequest;

import java.util.Optional;

/**
 * Lamp effect used by the presence rule.
 * A failed read is {@link Optional#empty()} so a dark arrival can still turn lights on.
 */
public interface EntranceLampPort {

    Optional<LampView> read(String deviceName);

    /**
     * @return false when this lamp did not accept the command
     */
    boolean apply(String deviceName, LightCommandRequest request);

    record LampView(boolean on, String color, Integer brightness) {
    }
}
