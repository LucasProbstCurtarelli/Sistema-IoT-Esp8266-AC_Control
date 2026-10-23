package com.iot.app.controller;

import com.iot.app.dto.LightLinksDocument;
import com.iot.app.service.LightLinkService;
import com.iot.app.util.ErrorResponseBuilder;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Shared lamp links for the panel.
 *
 * {@code GET} and {@code PUT /api/lights/links} use the same authentication as
 * the other {@code /api/lights} routes: {@code /api/**} requires a logged-in
 * caller, and an anonymous call receives the existing 401 entry point.
 */
@RestController
@RequestMapping("/api/lights/links")
public class LightLinkController {

    private static final Logger logger = LoggerFactory.getLogger(LightLinkController.class);

    private final LightLinkService lightLinkService;

    public LightLinkController(LightLinkService lightLinkService) {
        this.lightLinkService = lightLinkService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public LightLinksDocument getLinks() {
        return new LightLinksDocument(lightLinkService.getLinks());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> replaceLinks(@Valid @RequestBody LightLinksDocument body) {
        try {
            Map<String, List<String>> saved = lightLinkService.replaceLinks(body.getLinks());
            logger.info("Replaced light links for {} device(s)", saved.size());
            return ResponseEntity.ok(new LightLinksDocument(saved));
        } catch (IllegalArgumentException e) {
            logger.warn("Rejected light links: {}", e.getMessage());
            return ErrorResponseBuilder.buildErrorResponseEntity(false, e.getMessage(), 400);
        }
    }
}
