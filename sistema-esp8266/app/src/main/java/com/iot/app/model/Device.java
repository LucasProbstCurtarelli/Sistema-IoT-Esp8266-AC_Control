package com.iot.app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

/**
 * Device entity representing a smart device (e.g., smart bulb).
 * Uses UUID as primary key for better security and distributed system support.
 */
@Entity
@Table(name = "device")
@Data
public class Device {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci", length = 36)
    private UUID id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String deviceName;
    
    @Column(nullable = false)
    private String displayName;
    
    @Column(nullable = false)
    private String location;
    
    @Column(nullable = false)
    private String deviceType = "light"; // Default to light, can be extended for other device types
    
    @Column(nullable = false)
    private Boolean enabled = true;
}
