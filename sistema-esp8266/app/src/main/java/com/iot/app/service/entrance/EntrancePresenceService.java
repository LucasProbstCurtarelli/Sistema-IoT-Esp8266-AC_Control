package com.iot.app.service.entrance;

import com.iot.app.dto.LightCommandRequest;
import com.iot.app.service.AuthorizedKeyService;
import com.iot.app.service.EntranceSettingsService;
import com.iot.app.validation.EntranceUidNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Decides lamp effects from presence facts. The firmware only publishes facts.
 * The first empty fact arms a 5 s deadline. Later empty facts, including the
 * 2 s heartbeat, do not restart it. Only a present fact cancels it.
 */
@Service
public class EntrancePresenceService {

    static final String LAMP_1 = "lampada_1";
    static final String LAMP_2 = "lampada_2";
    static final String ALERT = "#FF0000";
    static final String WHITE = "#FFFFFF";

    private static final Logger logger = LoggerFactory.getLogger(EntrancePresenceService.class);

    private enum Phase {
        IDLE, OCCUPIED, PENDING, EMPTY
    }

    private final AuthorizedKeyService keys;
    private final EntranceSettingsService settings;
    private final EntranceLampPort lamps;
    private final AbsenceTimer timer;

    private Phase phase = Phase.IDLE;
    private String occupantUid;
    private boolean occupantRegistered;
    private boolean hasFailed;
    private Boolean failedOn;
    private String failedColor;
    private Integer failedBrightness;

    public EntrancePresenceService(
            AuthorizedKeyService keys,
            EntranceSettingsService settings,
            EntranceLampPort lamps,
            AbsenceTimer timer) {
        this.keys = keys;
        this.settings = settings;
        this.lamps = lamps;
        this.timer = timer;
    }

    public void onReading(boolean present, String uid, Integer lux) {
        synchronized (this) {
            if (!present) {
                handleAbsent();
                return;
            }
            String normalized = EntranceUidNormalizer.normalizeOrNull(uid);
            if (normalized == null) {
                return;
            }
            handlePresent(normalized);
        }
    }

    public void onAbsenceDue() {
        synchronized (this) {
            if (phase != Phase.PENDING) {
                return;
            }
            boolean wasUnknown = occupantUid != null && !occupantRegistered;
            boolean delivered = commandOff();
            if (delivered && wasUnknown) {
                settings.markPreferWhite();
            }
            phase = Phase.EMPTY;
            occupantUid = null;
            occupantRegistered = false;
            if (delivered) {
                logger.info("Presenca: suporte vazio, lampadas apagadas");
            }
        }
    }

    private void handleAbsent() {
        if (phase == Phase.PENDING || phase == Phase.EMPTY) {
            if (hasFailed && Boolean.FALSE.equals(failedOn)) {
                commandBoth(failedRequest());
            }
            return;
        }
        replayFailed();
        timer.arm(this::onAbsenceDue);
        phase = Phase.PENDING;
    }

    private void handlePresent(String uid) {
        timer.cancel();
        boolean registered = keys.isRegistered(uid);
        boolean sameOccupant = uid.equals(occupantUid)
                && (phase == Phase.OCCUPIED || phase == Phase.PENDING);
        if (sameOccupant) {
            phase = Phase.OCCUPIED;
            return;
        }

        replayFailed();
        boolean endingAlert = occupantUid != null
                && !occupantRegistered
                && registered
                && (phase == Phase.OCCUPIED || phase == Phase.PENDING);

        phase = Phase.OCCUPIED;
        occupantUid = uid;
        occupantRegistered = registered;
        if (registered) {
            logger.info("Presenca: chave aceita");
        } else {
            logger.info("Presenca: chave recusada");
        }

        if (!registered) {
            commandAlert();
            return;
        }
        if (endingAlert) {
            applyAfterAlert();
            return;
        }
        applyRegisteredArrival();
    }

    private void applyRegisteredArrival() {
        Optional<EntranceLampPort.LampView> left = readLamp(LAMP_1);
        Optional<EntranceLampPort.LampView> right = readLamp(LAMP_2);
        EntranceTuning tuning = settings.current();
        if (bothOnNonAlert(left, right) && !tuning.preferWhite()) {
            return;
        }
        boolean known = left.isPresent() && right.isPresent();
        boolean useWhite = tuning.preferWhite()
                || tuning.normalColor() == null
                || (known && bothRed(left, right));
        if (!known) {
            useWhite = tuning.preferWhite() || tuning.normalColor() == null;
        }
        if (useWhite) {
            commandWhiteAndRemember();
            return;
        }
        int brightness = tuning.normalBrightness() == null ? 100 : tuning.normalBrightness();
        commandBoth(on(tuning.normalColor(), brightness));
    }

    private void applyAfterAlert() {
        readLamp(LAMP_1);
        readLamp(LAMP_2);
        commandWhiteAndRemember();
    }

    private void commandAlert() {
        readLamp(LAMP_1);
        readLamp(LAMP_2);
        commandBoth(on(ALERT, 100));
    }

    private void commandWhiteAndRemember() {
        if (commandBoth(on(WHITE, 100))) {
            settings.rememberNormal(WHITE, 100);
        }
    }

    private boolean commandOff() {
        LightCommandRequest request = new LightCommandRequest();
        request.setState(false);
        return commandBoth(request);
    }

    private void replayFailed() {
        if (!hasFailed) {
            return;
        }
        LightCommandRequest request = failedRequest();
        if (commandBoth(request) && WHITE.equalsIgnoreCase(request.getColor()) && Boolean.TRUE.equals(request.getState())) {
            int brightness = request.getBrightness() == null ? 100 : request.getBrightness();
            settings.rememberNormal(WHITE, brightness);
        }
    }

    private boolean commandBoth(LightCommandRequest request) {
        boolean first = safeApply(LAMP_1, request);
        boolean second = safeApply(LAMP_2, request);
        if (first || second) {
            clearFailed();
            return true;
        }
        hasFailed = true;
        failedOn = request.getState();
        failedColor = request.getColor();
        failedBrightness = request.getBrightness();
        logger.warn("Presenca: ordem as lampadas nao entregue");
        return false;
    }

    private boolean safeApply(String deviceName, LightCommandRequest request) {
        try {
            return lamps.apply(deviceName, copy(request));
        } catch (RuntimeException ex) {
            logger.warn("Falha ao comandar {}", deviceName);
            return false;
        }
    }

    private Optional<EntranceLampPort.LampView> readLamp(String deviceName) {
        try {
            return lamps.read(deviceName);
        } catch (RuntimeException ex) {
            logger.warn("Falha ao ler estado de {}", deviceName);
            return Optional.empty();
        }
    }

    private static boolean bothOnNonAlert(
            Optional<EntranceLampPort.LampView> left,
            Optional<EntranceLampPort.LampView> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        EntranceLampPort.LampView a = left.get();
        EntranceLampPort.LampView b = right.get();
        return a.on() && b.on()
                && a.color() != null && b.color() != null
                && !alertColor(a.color()) && !alertColor(b.color());
    }

    private static boolean bothRed(
            Optional<EntranceLampPort.LampView> left,
            Optional<EntranceLampPort.LampView> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        return left.get().on() && right.get().on()
                && alertColor(left.get().color()) && alertColor(right.get().color());
    }

    private static boolean alertColor(String color) {
        return color != null && ALERT.equalsIgnoreCase(color.trim());
    }

    private LightCommandRequest failedRequest() {
        LightCommandRequest request = new LightCommandRequest();
        request.setState(failedOn);
        request.setColor(failedColor);
        request.setBrightness(failedBrightness);
        return request;
    }

    private void clearFailed() {
        hasFailed = false;
        failedOn = null;
        failedColor = null;
        failedBrightness = null;
    }

    private static LightCommandRequest on(String color, int brightness) {
        LightCommandRequest request = new LightCommandRequest();
        request.setState(true);
        request.setColor(color);
        request.setBrightness(brightness);
        return request;
    }

    private static LightCommandRequest copy(LightCommandRequest request) {
        LightCommandRequest copy = new LightCommandRequest();
        copy.setState(request.getState());
        copy.setColor(request.getColor());
        copy.setBrightness(request.getBrightness());
        return copy;
    }
}
