package com.iot.app.repository;

import com.iot.app.model.EntranceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntranceSettingsRepository extends JpaRepository<EntranceSettings, Integer> {
}
