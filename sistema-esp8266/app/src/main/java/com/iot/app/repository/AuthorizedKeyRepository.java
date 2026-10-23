package com.iot.app.repository;

import com.iot.app.model.AuthorizedKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthorizedKeyRepository extends JpaRepository<AuthorizedKey, UUID> {

    Optional<AuthorizedKey> findByUid(String uid);

    boolean existsByUid(String uid);

    List<AuthorizedKey> findAllByOrderByCreatedAtAsc();
}
