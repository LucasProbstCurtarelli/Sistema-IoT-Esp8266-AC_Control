package com.iot.app.service;

import com.iot.app.controller.EntranceAdminController;
import com.iot.app.dto.ChaveRequest;
import com.iot.app.dto.ChaveResponse;
import com.iot.app.exception.AuthorizedKeyNotFoundException;
import com.iot.app.exception.DuplicateAuthorizedKeyException;
import com.iot.app.exception.InvalidEntranceRequestException;
import com.iot.app.model.AuthorizedKey;
import com.iot.app.repository.AuthorizedKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthorizedKeyServiceTest {

    @Mock
    private AuthorizedKeyRepository repository;

    private final Map<String, AuthorizedKey> rows = new LinkedHashMap<>();
    private AuthorizedKeyService service;

    @BeforeEach
    void setUp() {
        lenient().when(repository.existsByUid(any())).thenAnswer(invocation ->
                rows.containsKey(invocation.getArgument(0)));
        lenient().when(repository.findByUid(any())).thenAnswer(invocation ->
                Optional.ofNullable(rows.get(invocation.getArgument(0))));
        lenient().when(repository.save(any(AuthorizedKey.class))).thenAnswer(invocation -> {
            AuthorizedKey row = invocation.getArgument(0);
            rows.put(row.getUid(), row);
            return row;
        });
        lenient().when(repository.findAllByOrderByCreatedAtAsc()).thenAnswer(invocation ->
                new ArrayList<>(rows.values()));
        lenient().doAnswer(invocation -> {
            AuthorizedKey row = invocation.getArgument(0);
            rows.remove(row.getUid());
            return null;
        }).when(repository).delete(any(AuthorizedKey.class));
        service = new AuthorizedKeyService(repository);
    }

    @Test
    void normalizesSeparatorsAndRejectsDuplicatesAndMalformedUids() {
        AuthorizedKey saved = service.add("04:a1:b2:c3", " Morador ");

        assertEquals("04A1B2C3", saved.getUid());
        assertEquals("Morador", saved.getRotulo());
        assertTrue(service.isRegistered("04a1b2c3"));
        assertThrows(DuplicateAuthorizedKeyException.class, () -> service.add("04A1B2C3", null));
        assertThrows(InvalidEntranceRequestException.class, () -> service.add("xyz", null));
        assertThrows(InvalidEntranceRequestException.class, () -> service.add("123", null));
        assertFalse(service.isRegistered("not-a-uid"));
    }

    @Test
    void removalAndANewServiceInstanceKeepTheRepositoryRows() {
        service.add("04A1B2C3", null);
        service.remove("04a1b2c3");
        assertFalse(service.isRegistered("04A1B2C3"));
        assertThrows(AuthorizedKeyNotFoundException.class, () -> service.remove("04A1B2C3"));

        service.add("AABBCCDD", "Visita");
        AuthorizedKeyService reloaded = new AuthorizedKeyService(repository);

        assertTrue(reloaded.isRegistered("aabbccdd"));
        assertEquals(1, reloaded.list().size());
        assertEquals("AABBCCDD", reloaded.list().get(0).getUid());
    }

    @Test
    void adminControllerReturnsCreatedAndMapsConflicts() {
        EntranceAdminController controller = new EntranceAdminController(service, null);
        ResponseEntity<ChaveResponse> created = controller.addKey(new ChaveRequest("04a1b2c3", "Morador"));

        assertEquals(201, created.getStatusCode().value());
        assertEquals("04A1B2C3", created.getBody().uid());

        DuplicateAuthorizedKeyException duplicate = assertThrows(
                DuplicateAuthorizedKeyException.class,
                () -> controller.addKey(new ChaveRequest("04A1B2C3", null)));
        assertEquals(409, controller.conflict(duplicate).getStatusCode().value());

        InvalidEntranceRequestException invalid = assertThrows(
                InvalidEntranceRequestException.class,
                () -> controller.addKey(new ChaveRequest("nope", null)));
        assertEquals(400, controller.badRequest(invalid).getStatusCode().value());

        assertEquals(204, controller.removeKey("04A1B2C3").getStatusCode().value());
        AuthorizedKeyNotFoundException missing = assertThrows(
                AuthorizedKeyNotFoundException.class,
                () -> controller.removeKey("04A1B2C3"));
        assertEquals(404, controller.missing(missing).getStatusCode().value());
    }
}
