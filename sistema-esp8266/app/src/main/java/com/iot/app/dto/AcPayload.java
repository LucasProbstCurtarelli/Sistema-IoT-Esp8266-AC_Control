package com.iot.app.dto;

import lombok.Data;

@Data
public class AcPayload {
    private String power; // "on" ou "off"
    private int temp;     // ex: 22
}