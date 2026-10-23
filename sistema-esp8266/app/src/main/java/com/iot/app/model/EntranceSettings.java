package com.iot.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Singleton calibration for the entrance hook.
 * {@code luxThreshold} null means a resident arrival is not dark.
 * {@code normalColor} is never the red alert.
 */
@Entity
@Table(name = "ajuste_entrada")
@Data
@NoArgsConstructor
public class EntranceSettings {

    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lux_threshold")
    private Integer luxThreshold;

    @Column(name = "normal_color", length = 7)
    private String normalColor;

    @Column(name = "normal_brightness")
    private Integer normalBrightness;

    @Column(name = "prefer_white", nullable = false)
    private boolean preferWhite;
}
