package com.iot.app.validation;

import com.iot.app.exception.InvalidEntranceRequestException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * RFID uids are compared as uppercase hex without separators.
 */
public final class EntranceUidNormalizer {

    private static final Pattern UID = Pattern.compile("^[0-9A-F]{8,20}$");

    private EntranceUidNormalizer() {
    }

    public static String normalizeOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String compact = raw.replace(":", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
        if (!UID.matcher(compact).matches()) {
            return null;
        }
        return compact;
    }

    public static String require(String raw) {
        String normalized = normalizeOrNull(raw);
        if (normalized == null) {
            throw new InvalidEntranceRequestException(
                    "UID must be 8 to 20 hexadecimal characters");
        }
        return normalized;
    }
}
