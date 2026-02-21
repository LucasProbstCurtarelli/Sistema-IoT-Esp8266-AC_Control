package com.iot.app.repository;

import com.iot.app.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {
    
    /**
     * Find device by device name (e.g., "lampada_1").
     */
    Optional<Device> findByDeviceName(String deviceName);
    
    /**
     * Check if device exists by device name.
     */
    boolean existsByDeviceName(String deviceName);
    
    /**
     * Find all enabled devices.
     */
    java.util.List<Device> findByEnabledTrue();
}
