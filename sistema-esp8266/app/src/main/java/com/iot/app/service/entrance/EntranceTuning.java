package com.iot.app.service.entrance;

/**
 * Copy of the singleton entrance row used by one decision.
 */
public record EntranceTuning(
        Integer luxThreshold,
        String normalColor,
        Integer normalBrightness,
        boolean preferWhite) {
}
