package com.codesync.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    // Empty for accounts created with Google sign-in. Empty rather than null so that databases
    // created before Google sign-in, where this column is NOT NULL, need no migration.
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Google's permanent id for the person ("sub" in the ID token). Emails can change; this does not.
    @Column(name = "google_subject", unique = true)
    private String googleSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public static User fromGoogle(String username, String email, String googleSubject) {
        User user = new User(username, email, "");
        user.googleSubject = googleSubject;
        return user;
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}