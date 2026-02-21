package com.iot.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for device information response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {
    private String id;
    private String deviceName;
    private String displayName;
    private String location;
    private String deviceType;
    private Boolean enabled;
}
