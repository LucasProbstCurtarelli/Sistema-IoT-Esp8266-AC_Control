package com.iot.app.service;

import com.iot.app.exception.InvalidEntranceRequestException;
import com.iot.app.model.EntranceSettings;
import com.iot.app.repository.EntranceSettingsRepository;
import com.iot.app.service.entrance.EntranceTuning;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class EntranceSettingsService {

    static final String ALERT_COLOR = "#FF0000";

    private final EntranceSettingsRepository repository;

    public EntranceSettingsService(EntranceSettingsRepository repository) {
        this.repository = repository;
    }

    public EntranceTuning current() {
        EntranceSettings row = loadOrCreate();
        return new EntranceTuning(
                row.getLuxThreshold(),
                row.getNormalColor(),
                row.getNormalBrightness(),
                row.isPreferWhite());
    }

    public Integer getThreshold() {
        return loadOrCreate().getLuxThreshold();
    }

    public void setThreshold(Integer threshold) {
        if (threshold != null && (threshold < 0 || threshold > 1023)) {
            throw new InvalidEntranceRequestException("threshold must be an integer from 0 to 1023, or null");
        }
        EntranceSettings row = loadOrCreate();
        row.setLuxThreshold(threshold);
        repository.save(row);
    }

    public void markPreferWhite() {
        EntranceSettings row = loadOrCreate();
        row.setPreferWhite(true);
        repository.save(row);
    }

    public void rememberNormal(String color, int brightness) {
        EntranceSettings row = loadOrCreate();
        row.setNormalColor(color);
        row.setNormalBrightness(brightness);
        row.setPreferWhite(false);
        repository.save(row);
    }

    /**
     * A completed manual command stores the resident color.
     * Alert red is ignored so it cannot become the remembered color.
     */
    public void rememberManualColor(String color, Integer brightness) {
        if (color == null || color.isBlank()) {
            return;
        }
        String normalized = color.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^#[0-9A-F]{6}$")) {
            return;
        }
        if (ALERT_COLOR.equals(normalized)) {
            return;
        }
        int storedBrightness = brightness == null ? 100 : brightness;
        rememberNormal(normalized, storedBrightness);
    }

    private EntranceSettings loadOrCreate() {
        return repository.findById(EntranceSettings.SINGLETON_ID).orElseGet(() -> {
            EntranceSettings created = new EntranceSettings();
            created.setId(EntranceSettings.SINGLETON_ID);
            created.setPreferWhite(false);
            return repository.save(created);
        });
    }
}
