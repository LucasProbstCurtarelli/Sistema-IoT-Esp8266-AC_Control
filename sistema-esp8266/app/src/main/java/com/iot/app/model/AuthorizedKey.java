package com.iot.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One RFID uid that follows the resident rule.
 * The label is optional; the comparison key is {@code uid}.
 */
@Entity
@Table(name = "chave_autorizada")
@Data
@NoArgsConstructor
public class AuthorizedKey {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "uid", nullable = false, unique = true, length = 20)
    private String uid;

    @Column(name = "rotulo", length = 100)
    private String rotulo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
