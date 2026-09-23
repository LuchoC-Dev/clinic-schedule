package com.clinic.schedule.security;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A user of the application. Read-only here: in v1 users come from the seed and there is no
 * screen to manage them (D-04), so nothing in the code creates or changes one.
 *
 * <p>Named {@code AppUser}, like its table, so it cannot be confused with Spring Security's own
 * {@code User}.
 */
@Entity
@Table(name = "app_user")
class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** The practitioner this user stands for; null for reception. */
    @Column(name = "practitioner_id")
    private UUID practitionerId;

    @Column(nullable = false)
    private boolean active;

    protected AppUser() {
        // required by JPA
    }

    UUID getId() {
        return id;
    }

    String getUsername() {
        return username;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    Role getRole() {
        return role;
    }

    UUID getPractitionerId() {
        return practitionerId;
    }

    boolean isActive() {
        return active;
    }
}
