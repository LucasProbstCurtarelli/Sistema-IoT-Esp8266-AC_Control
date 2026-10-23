package com.iot.app.controller;

import com.iot.app.dto.ChaveRequest;
import com.iot.app.dto.ChaveResponse;
import com.iot.app.dto.ChavesResponse;
import com.iot.app.dto.LimiarRequest;
import com.iot.app.dto.LimiarResponse;
import com.iot.app.exception.AuthorizedKeyNotFoundException;
import com.iot.app.exception.DuplicateAuthorizedKeyException;
import com.iot.app.exception.InvalidEntranceRequestException;
import com.iot.app.model.AuthorizedKey;
import com.iot.app.service.AuthorizedKeyService;
import com.iot.app.service.EntranceSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin allowlist and lux threshold. {@code /api/admin/**} already requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/entrada")
public class EntranceAdminController {

    private final AuthorizedKeyService keys;
    private final EntranceSettingsService settings;

    public EntranceAdminController(AuthorizedKeyService keys, EntranceSettingsService settings) {
        this.keys = keys;
        this.settings = settings;
    }

    @GetMapping("/chaves")
    public ChavesResponse listKeys() {
        return new ChavesResponse(keys.list().stream()
                .map(EntranceAdminController::toResponse)
                .toList());
    }

    @PostMapping("/chaves")
    public ResponseEntity<ChaveResponse> addKey(@RequestBody ChaveRequest request) {
        if (request == null || request.uid() == null) {
            throw new InvalidEntranceRequestException("UID must be 8 to 20 hexadecimal characters");
        }
        AuthorizedKey saved = keys.add(request.uid(), request.rotulo());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @DeleteMapping("/chaves/{uid}")
    public ResponseEntity<Void> removeKey(@PathVariable String uid) {
        keys.remove(uid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/limiar")
    public LimiarResponse getThreshold() {
        return new LimiarResponse(settings.getThreshold());
    }

    @PutMapping("/limiar")
    public LimiarResponse putThreshold(@RequestBody LimiarRequest request) {
        if (request == null) {
            throw new InvalidEntranceRequestException("threshold must be an integer from 0 to 1023, or null");
        }
        settings.setThreshold(request.threshold());
        return new LimiarResponse(settings.getThreshold());
    }

    @ExceptionHandler(InvalidEntranceRequestException.class)
    public ResponseEntity<Map<String, String>> badRequest(InvalidEntranceRequestException ex) {
        return ResponseEntity.badRequest().body(message(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateAuthorizedKeyException.class)
    public ResponseEntity<Map<String, String>> conflict(DuplicateAuthorizedKeyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(message(ex.getMessage()));
    }

    @ExceptionHandler(AuthorizedKeyNotFoundException.class)
    public ResponseEntity<Map<String, String>> missing(AuthorizedKeyNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(ex.getMessage()));
    }

    private static ChaveResponse toResponse(AuthorizedKey key) {
        return new ChaveResponse(key.getUid(), key.getRotulo());
    }

    private static Map<String, String> message(String text) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", text);
        return body;
    }
}
