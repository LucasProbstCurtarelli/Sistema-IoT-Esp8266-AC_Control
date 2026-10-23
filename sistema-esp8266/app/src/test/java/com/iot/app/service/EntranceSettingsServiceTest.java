package com.iot.app.service;

import com.iot.app.controller.EntranceAdminController;
import com.iot.app.dto.LimiarRequest;
import com.iot.app.exception.InvalidEntranceRequestException;
import com.iot.app.model.EntranceSettings;
import com.iot.app.repository.EntranceSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EntranceSettingsServiceTest {

    @Mock
    private EntranceSettingsRepository repository;

    private EntranceSettings stored;
    private EntranceSettingsService service;

    @BeforeEach
    void setUp() {
        lenient().when(repository.findById(EntranceSettings.SINGLETON_ID)).thenAnswer(invocation ->
                Optional.ofNullable(stored));
        lenient().when(repository.save(any(EntranceSettings.class))).thenAnswer(invocation -> {
            stored = invocation.getArgument(0);
            return stored;
        });
        service = new EntranceSettingsService(repository);
    }

    @Test
    void thresholdSurvivesANewServiceAndRejectsValuesOutsideTheAdc() {
        assertNull(service.getThreshold());

        service.setThreshold(350);
        EntranceSettingsService reloaded = new EntranceSettingsService(repository);
        assertEquals(350, reloaded.getThreshold());

        reloaded.setThreshold(null);
        assertNull(new EntranceSettingsService(repository).getThreshold());
        assertThrows(InvalidEntranceRequestException.class, () -> service.setThreshold(-1));
        assertThrows(InvalidEntranceRequestException.class, () -> service.setThreshold(1024));
    }

    @Test
    void manualColorIsRememberedAndAlertRedIsNot() {
        service.markPreferWhite();
        service.rememberManualColor("#112233", 55);

        assertEquals("#112233", service.current().normalColor());
        assertEquals(55, service.current().normalBrightness());
        assertFalse(service.current().preferWhite());

        service.rememberManualColor("#FF0000", 100);

        assertEquals("#112233", service.current().normalColor());
        assertTrue(new EntranceSettingsService(repository).current().normalColor().equals("#112233"));
    }

    @Test
    void adminThresholdEndpointRoundTrips() {
        EntranceAdminController controller = new EntranceAdminController(null, service);

        assertNull(controller.getThreshold().threshold());
        assertEquals(350, controller.putThreshold(new LimiarRequest(350)).threshold());
        assertEquals(350, controller.getThreshold().threshold());
        assertThrows(InvalidEntranceRequestException.class,
                () -> controller.putThreshold(new LimiarRequest(2000)));
    }
}
