package com.iot.app.service;

import com.iot.app.dto.LightCommandRequest;
import com.iot.app.model.AuthorizedKey;
import com.iot.app.model.EntranceSettings;
import com.iot.app.repository.AuthorizedKeyRepository;
import com.iot.app.repository.EntranceSettingsRepository;
import com.iot.app.service.entrance.AbsenceTimer;
import com.iot.app.service.entrance.EntranceLampPort;
import com.iot.app.service.entrance.EntrancePresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EntrancePresenceServiceTest {

    private static final String RESIDENT = "04A1B2C3";
    private static final String OTHER = "11223344";
    private static final String UNKNOWN = "AABBCCDD";

    @Mock
    private AuthorizedKeyRepository keyRepository;

    @Mock
    private EntranceSettingsRepository settingsRepository;

    private final Map<String, AuthorizedKey> keys = new LinkedHashMap<>();
    private EntranceSettings storedSettings;
    private FakeLamps lamps;
    private ManualAbsenceTimer timer;
    private EntranceSettingsService settings;
    private EntrancePresenceService service;

    @BeforeEach
    void setUp() {
        lenient().when(keyRepository.existsByUid(any())).thenAnswer(invocation ->
                keys.containsKey(invocation.getArgument(0)));
        lenient().when(keyRepository.findByUid(any())).thenAnswer(invocation ->
                Optional.ofNullable(keys.get(invocation.getArgument(0))));
        lenient().when(keyRepository.save(any(AuthorizedKey.class))).thenAnswer(invocation -> {
            AuthorizedKey row = invocation.getArgument(0);
            keys.put(row.getUid(), row);
            return row;
        });
        lenient().when(keyRepository.findAllByOrderByCreatedAtAsc()).thenAnswer(invocation ->
                new ArrayList<>(keys.values()));
        lenient().doAnswer(invocation -> {
            AuthorizedKey row = invocation.getArgument(0);
            keys.remove(row.getUid());
            return null;
        }).when(keyRepository).delete(any(AuthorizedKey.class));

        lenient().when(settingsRepository.findById(EntranceSettings.SINGLETON_ID)).thenAnswer(invocation ->
                Optional.ofNullable(storedSettings));
        lenient().when(settingsRepository.save(any(EntranceSettings.class))).thenAnswer(invocation -> {
            storedSettings = invocation.getArgument(0);
            return storedSettings;
        });

        AuthorizedKeyService allowlist = new AuthorizedKeyService(keyRepository);
        settings = new EntranceSettingsService(settingsRepository);
        allowlist.add(RESIDENT, "Morador");
        allowlist.add(OTHER, "Outro");
        settings.setThreshold(400);

        lamps = new FakeLamps();
        timer = new ManualAbsenceTimer();
        service = new EntrancePresenceService(allowlist, settings, lamps, timer);
    }

    @Test
    void darkArrivalWithoutRememberedColorTurnsBothLampsWhite() {
        service.onReading(true, RESIDENT, 100);

        assertBothOn("#FFFFFF", 100);
        assertFalse(timer.isArmed());
    }

    @Test
    void darkArrivalUsesRememberedColorWhenLampsAreOff() {
        settings.rememberNormal("#3366FF", 40);

        service.onReading(true, RESIDENT, 100);

        assertBothOn("#3366FF", 40);
    }

    @Test
    void darkArrivalLeavesAnAlreadyOnNonAlertColorAlone() {
        settings.rememberNormal("#3366FF", 40);
        lamps.state.put("lampada_1", new EntranceLampPort.LampView(true, "#3366FF", 40));
        lamps.state.put("lampada_2", new EntranceLampPort.LampView(true, "#3366FF", 40));

        service.onReading(true, RESIDENT, 100);

        assertTrue(lamps.attempts.isEmpty());
    }

    @Test
    void arrivalWithoutALightReadingTurnsBothLampsWhite() {
        service.onReading(true, RESIDENT, null);

        assertBothOn("#FFFFFF", 100);
    }

    @Test
    void failedReadStillTurnsRememberedColorOnInTheDark() {
        settings.rememberNormal("#3366FF", 40);
        lamps.failRead = true;

        service.onReading(true, RESIDENT, 100);

        assertBothOn("#3366FF", 40);
    }

    @Test
    void repeatedEmptyFactsArmOnceAndTheSingleDeadlineTurnsBothOff() {
        service.onReading(true, RESIDENT, 100);
        lamps.attempts.clear();

        service.onReading(false, null, 100);
        service.onReading(false, null, 100);
        service.onReading(false, null, 100);

        assertEquals(1, timer.armCalls);
        assertTrue(timer.isArmed());
        timer.fire();

        assertBothOff();
        assertFalse(settings.current().preferWhite());
    }

    @Test
    void sameUidBeforeTheDeadlineCancelsAndDoesNotTurnOff() {
        service.onReading(true, RESIDENT, 100);
        service.onReading(false, null, 100);
        assertTrue(timer.isArmed());

        service.onReading(true, RESIDENT, 100);

        assertFalse(timer.isArmed());
        timer.fire();
        assertTrue(lamps.state.get("lampada_1").on());
        assertTrue(lamps.state.get("lampada_2").on());
    }

    @Test
    void unknownKeyCommandsRedWithOrWithoutLuxAndHeartbeatDoesNotRepeat() {
        service.onReading(true, UNKNOWN, null);
        assertBothOn("#FF0000", 100);
        int sent = lamps.attempts.size();

        service.onReading(true, UNKNOWN, 10);

        assertEquals(sent, lamps.attempts.size());
        assertEquals(null, settings.current().normalColor());
    }

    @Test
    void directSwapFromUnknownToRegisteredTurnsBothLampsWhite() {
        service.onReading(true, UNKNOWN, 900);
        lamps.attempts.clear();

        service.onReading(true, RESIDENT, 100);

        assertBothOn("#FFFFFF", 100);
        assertFalse(timer.isArmed());

        lamps.attempts.clear();
        service.onReading(true, UNKNOWN, 900);
        lamps.attempts.clear();
        service.onReading(true, RESIDENT, 900);

        assertBothOn("#FFFFFF", 100);
        assertFalse(settings.current().preferWhite());
    }

    @Test
    void unknownDepartureMakesTheNextDarkArrivalWhiteInsteadOfTheOldColor() {
        settings.rememberNormal("#3366FF", 40);
        service.onReading(true, UNKNOWN, 10);
        service.onReading(false, null, 10);
        timer.fire();

        assertTrue(settings.current().preferWhite());
        assertEquals("#3366FF", settings.current().normalColor());

        lamps.attempts.clear();
        service.onReading(true, RESIDENT, 100);

        assertBothOn("#FFFFFF", 100);
        assertFalse(settings.current().preferWhite());
        assertEquals("#FFFFFF", settings.current().normalColor());
    }

    @Test
    void registeredKeySwappedForUnknownTurnsRedImmediately() {
        service.onReading(true, RESIDENT, 100);
        lamps.attempts.clear();

        service.onReading(true, UNKNOWN, 900);

        assertBothOn("#FF0000", 100);
        assertFalse(timer.isArmed());
        assertEquals("#FFFFFF", settings.current().normalColor());
    }

    @Test
    void swappingRegisteredKeysInTheLightDoesNotCommand() {
        service.onReading(true, RESIDENT, 100);
        lamps.attempts.clear();

        service.onReading(true, OTHER, 800);

        assertTrue(lamps.attempts.isEmpty());
    }

    @Test
    void oneLampFailureStillCommandsTheOther() {
        lamps.failDevices.add("lampada_2");

        service.onReading(true, RESIDENT, 100);

        assertTrue(lamps.attempts.contains("lampada_1|true|#FFFFFF|100"));
        assertTrue(lamps.attempts.contains("lampada_2|true|#FFFFFF|100"));
        assertTrue(lamps.state.get("lampada_1").on());
        assertFalse(lamps.state.containsKey("lampada_2"));
    }

    @Test
    void bothFailuresAreSentAgainOnTheNextEmptyFact() {
        lamps.failAll = true;
        service.onReading(true, RESIDENT, 100);
        assertEquals(2, lamps.attempts.size());

        lamps.failAll = false;
        lamps.attempts.clear();
        service.onReading(false, null, 100);

        assertBothOn("#FFFFFF", 100);
        assertTrue(timer.isArmed());
    }

    @Test
    void unreadableUidDoesNotCommand() {
        service.onReading(true, "xyz", 100);

        assertTrue(lamps.attempts.isEmpty());
    }

    private void assertBothOn(String color, int brightness) {
        String token = "|true|" + color + "|" + brightness;
        assertTrue(lamps.attempts.contains("lampada_1" + token), lamps.attempts.toString());
        assertTrue(lamps.attempts.contains("lampada_2" + token), lamps.attempts.toString());
    }

    private void assertBothOff() {
        assertTrue(lamps.attempts.contains("lampada_1|false|null|null"), lamps.attempts.toString());
        assertTrue(lamps.attempts.contains("lampada_2|false|null|null"), lamps.attempts.toString());
    }

    private static final class ManualAbsenceTimer implements AbsenceTimer {
        private Runnable task;
        private boolean armed;
        private int armCalls;

        @Override
        public boolean arm(Runnable onFire) {
            armCalls++;
            if (armed) {
                return false;
            }
            armed = true;
            task = onFire;
            return true;
        }

        @Override
        public void cancel() {
            armed = false;
            task = null;
        }

        @Override
        public boolean isArmed() {
            return armed;
        }

        void fire() {
            if (!armed || task == null) {
                return;
            }
            Runnable pending = task;
            armed = false;
            task = null;
            pending.run();
        }
    }

    private static final class FakeLamps implements EntranceLampPort {
        private final Map<String, LampView> state = new HashMap<>();
        private final List<String> attempts = new ArrayList<>();
        private final Set<String> failDevices = new HashSet<>();
        private boolean failRead;
        private boolean failAll;

        @Override
        public Optional<LampView> read(String deviceName) {
            if (failRead) {
                return Optional.empty();
            }
            LampView current = state.get(deviceName);
            if (current == null) {
                return Optional.of(new LampView(false, null, null));
            }
            return Optional.of(current);
        }

        @Override
        public boolean apply(String deviceName, LightCommandRequest request) {
            attempts.add(deviceName + "|" + request.getState() + "|" + request.getColor() + "|" + request.getBrightness());
            if (failAll || failDevices.contains(deviceName)) {
                return false;
            }
            boolean on = Boolean.TRUE.equals(request.getState());
            LampView previous = state.get(deviceName);
            if (!on) {
                state.put(deviceName, new LampView(
                        false,
                        previous == null ? null : previous.color(),
                        previous == null ? null : previous.brightness()));
            } else {
                state.put(deviceName, new LampView(
                        true,
                        request.getColor() != null ? request.getColor() : (previous == null ? null : previous.color()),
                        request.getBrightness() != null
                                ? request.getBrightness()
                                : (previous == null ? null : previous.brightness())));
            }
            return true;
        }
    }
}
