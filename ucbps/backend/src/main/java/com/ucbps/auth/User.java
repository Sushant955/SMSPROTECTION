package com.ucbps.auth;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 254)
    public String email;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(nullable = false, length = 20)
    public String role = "USER";

    @Column(length = 2)
    public String country;

    @Column(name = "reporting_consent", nullable = false)
    public boolean reportingConsent;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
