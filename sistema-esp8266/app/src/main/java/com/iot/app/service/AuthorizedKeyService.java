package com.iot.app.service;

import com.iot.app.exception.AuthorizedKeyNotFoundException;
import com.iot.app.exception.DuplicateAuthorizedKeyException;
import com.iot.app.exception.InvalidEntranceRequestException;
import com.iot.app.model.AuthorizedKey;
import com.iot.app.repository.AuthorizedKeyRepository;
import com.iot.app.validation.EntranceUidNormalizer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthorizedKeyService {

    private final AuthorizedKeyRepository repository;

    public AuthorizedKeyService(AuthorizedKeyRepository repository) {
        this.repository = repository;
    }

    public List<AuthorizedKey> list() {
        return repository.findAllByOrderByCreatedAtAsc();
    }

    public AuthorizedKey add(String rawUid, String rotulo) {
        String uid = EntranceUidNormalizer.require(rawUid);
        if (repository.existsByUid(uid)) {
            throw new DuplicateAuthorizedKeyException(uid);
        }
        AuthorizedKey row = new AuthorizedKey();
        row.setId(UUID.randomUUID());
        row.setUid(uid);
        row.setRotulo(cleanLabel(rotulo));
        row.setCreatedAt(Instant.now());
        return repository.save(row);
    }

    public void remove(String rawUid) {
        String uid = EntranceUidNormalizer.require(rawUid);
        AuthorizedKey row = repository.findByUid(uid)
                .orElseThrow(() -> new AuthorizedKeyNotFoundException(uid));
        repository.delete(row);
    }

    public boolean isRegistered(String rawUid) {
        String uid = EntranceUidNormalizer.normalizeOrNull(rawUid);
        if (uid == null) {
            return false;
        }
        return repository.existsByUid(uid);
    }

    private static String cleanLabel(String rotulo) {
        if (rotulo == null) {
            return null;
        }
        String trimmed = rotulo.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 100) {
            throw new InvalidEntranceRequestException("rotulo must be at most 100 characters");
        }
        return trimmed;
    }
}
