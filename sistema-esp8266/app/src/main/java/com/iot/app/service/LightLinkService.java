package com.iot.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.app.model.LightLinkMap;
import com.iot.app.repository.LightLinkMapRepository;
import com.iot.app.validation.DeviceNameValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replaces and reads the lamp link map shared by every browser.
 * The stored document is the map the client sent. A link from A to B is not
 * mirrored into a link from B to A.
 */
@Service
public class LightLinkService {

    static final int MAX_SOURCES = 50;
    static final int MAX_TARGETS = 50;

    private static final TypeReference<LinkedHashMap<String, List<String>>> LINKS_TYPE =
            new TypeReference<>() {};

    private final LightLinkMapRepository repository;
    private final ObjectMapper objectMapper;

    public LightLinkService(LightLinkMapRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, List<String>> getLinks() {
        return repository.findById(LightLinkMap.SINGLETON_ID)
                .map(row -> parse(row.getLinksJson()))
                .orElseGet(LinkedHashMap::new);
    }

    @Transactional
    public Map<String, List<String>> replaceLinks(Map<String, List<String>> links) {
        LinkedHashMap<String, List<String>> copy = validateAndCopy(links);
        String json = write(copy);

        LightLinkMap row = repository.findById(LightLinkMap.SINGLETON_ID).orElseGet(LightLinkMap::new);
        row.setId(LightLinkMap.SINGLETON_ID);
        row.setLinksJson(json);
        repository.save(row);
        return copy;
    }

    private LinkedHashMap<String, List<String>> validateAndCopy(Map<String, List<String>> links) {
        if (links == null) {
            throw new IllegalArgumentException("links is required");
        }
        if (links.size() > MAX_SOURCES) {
            throw new IllegalArgumentException("Too many linked devices");
        }

        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : links.entrySet()) {
            requireDeviceName(entry.getKey());
            List<String> targets = entry.getValue();
            if (targets == null) {
                throw new IllegalArgumentException("Link list cannot be null");
            }
            if (targets.size() > MAX_TARGETS) {
                throw new IllegalArgumentException("Too many links for one device");
            }
            List<String> copiedTargets = new ArrayList<>(targets.size());
            for (String target : targets) {
                requireDeviceName(target);
                copiedTargets.add(target);
            }
            copy.put(entry.getKey(), copiedTargets);
        }
        return copy;
    }

    private void requireDeviceName(String deviceName) {
        if (!DeviceNameValidator.isAcceptable(deviceName)) {
            throw new IllegalArgumentException(
                    "Device name must contain only alphanumeric characters, underscores, and hyphens (1-50 characters)");
        }
    }

    private String write(Map<String, List<String>> links) {
        try {
            return objectMapper.writeValueAsString(links);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Light links could not be stored", e);
        }
    }

    private Map<String, List<String>> parse(String linksJson) {
        if (linksJson == null || linksJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            LinkedHashMap<String, List<String>> parsed = objectMapper.readValue(linksJson, LINKS_TYPE);
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored light links could not be read", e);
        }
    }
}
