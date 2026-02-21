package com.iot.app.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "user")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(unique = true)
    private String username;
    private String password;
    private String role;
}