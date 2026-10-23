package com.iot.app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Body of {@code GET} and {@code PUT /api/lights/links}.
 * Shape: {@code { "links": { "lampada_1": ["lampada_2"] } }}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LightLinksDocument {

    @NotNull(message = "links is required")
    private Map<String, List<String>> links;
}
