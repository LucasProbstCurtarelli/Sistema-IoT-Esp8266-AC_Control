package com.iot.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.app.dto.LightLinksDocument;
import com.iot.app.model.LightLinkMap;
import com.iot.app.repository.LightLinkMapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LightLinkServiceTest {

    @Mock
    private LightLinkMapRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LightLinkService service;
    private LightLinkMap stored;

    @BeforeEach
    void setUp() {
        stored = null;
        service = new LightLinkService(repository, objectMapper);
        lenient().when(repository.findById(LightLinkMap.SINGLETON_ID))
                .thenAnswer(invocation -> Optional.ofNullable(stored));
        lenient().when(repository.save(any(LightLinkMap.class))).thenAnswer(invocation -> {
            stored = invocation.getArgument(0);
            return stored;
        });
    }

    @Test
    void getLinksReturnsEmptyMapWhenNothingIsStored() {
        Map<String, List<String>> links = service.getLinks();

        assertTrue(links.isEmpty());
    }

    @Test
    void replacePersistsTheMapAsGivenAndRoundTripsThroughGet() throws Exception {
        LinkedHashMap<String, List<String>> incoming = new LinkedHashMap<>();
        incoming.put("lampada_1", List.of("lampada_2"));

        Map<String, List<String>> saved = service.replaceLinks(incoming);

        assertEquals(List.of("lampada_2"), saved.get("lampada_1"));
        assertFalse(saved.containsKey("lampada_2"));
        assertEquals(List.of("lampada_2"), service.getLinks().get("lampada_1"));
        assertEquals(
                "{\"links\":{\"lampada_1\":[\"lampada_2\"]}}",
                objectMapper.writeValueAsString(new LightLinksDocument(service.getLinks())));
    }

    @Test
    void emptyMapIsValidAndReplacesThePreviousDocument() {
        LinkedHashMap<String, List<String>> first = new LinkedHashMap<>();
        first.put("lampada_1", List.of("lampada_2"));
        first.put("lampada_2", List.of("lampada_1"));
        service.replaceLinks(first);

        Map<String, List<String>> saved = service.replaceLinks(Map.of());

        assertTrue(saved.isEmpty());
        assertTrue(service.getLinks().isEmpty());
    }

    @Test
    void invalidDeviceNameIsRejectedAndDoesNotWrite() {
        LinkedHashMap<String, List<String>> incoming = new LinkedHashMap<>();
        incoming.put("lampada 1", List.of("lampada_2"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> service.replaceLinks(incoming));

        assertTrue(error.getMessage().contains("Device name"));
        verify(repository, never()).save(any());
    }

    @Test
    void nullTargetListIsRejected() {
        LinkedHashMap<String, List<String>> incoming = new LinkedHashMap<>();
        incoming.put("lampada_1", null);

        assertThrows(IllegalArgumentException.class, () -> service.replaceLinks(incoming));
        verify(repository, never()).save(any());
    }

    @Test
    void hyphenatedNamesAndDuplicateTargetsAreStoredAsSent() {
        LinkedHashMap<String, List<String>> incoming = new LinkedHashMap<>();
        incoming.put("lampada-1", List.of("lampada_2", "lampada_2"));

        Map<String, List<String>> saved = service.replaceLinks(incoming);

        assertEquals(List.of("lampada_2", "lampada_2"), saved.get("lampada-1"));
    }

    @Test
    void tooManyTargetsIsRejected() {
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < LightLinkService.MAX_TARGETS + 1; i++) {
            targets.add("lampada_2");
        }
        LinkedHashMap<String, List<String>> incoming = new LinkedHashMap<>();
        incoming.put("lampada_1", targets);

        assertThrows(IllegalArgumentException.class, () -> service.replaceLinks(incoming));
        verify(repository, never()).save(any());
    }
}
