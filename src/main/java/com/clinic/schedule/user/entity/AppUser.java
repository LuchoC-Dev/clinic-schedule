package com.clinic.schedule.user.entity;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.clinic.schedule.user.api.Role;

/**
 * A user of the application: one person, with one role (D-68).
 *
 * <p>What is recorded across the application names a user by their username, so the username
 * never changes and a user is never deleted: they are deactivated, and may be reactivated. The
 * role and the practitioner a user stands for never change either; a user who needs another is
 * deactivated and a new one created. What needs more than the user itself to be checked — that a
 * username was never used, that a practitioner has no other active user — is the service's job.
 *
 * <p>Every change returns the entry that records it, with who made it and when, so no change can
 * be made without one.
 *
 * <p>A password an admin sets — creating the user, or resetting theirs — is temporary: the user
 * must change it before doing anything else, so nobody but them knows the one they use (D-69).
 *
 * <p>Named {@code AppUser}, like its table, so it cannot be confused with Spring Security's own
 * {@code User}.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    /** As long as the column that stores it. */
    static final int MAX_USERNAME_LENGTH = 50;

    /**
     * Lower-case letters, digits, dots, hyphens and underscores, from 3 characters: what is typed at
     * the login form, and what the records show, with nothing that two people could read alike.
     */
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3," + MAX_USERNAME_LENGTH + "}");

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = MAX_USERNAME_LENGTH)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** The practitioner this user stands for; null for any other role. */
    @Column(name = "practitioner_id")
    private UUID practitionerId;

    @Column(nullable = false)
    private boolean active;

    /** Whether the password was set by an admin, and must be changed at the next login. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    protected AppUser() {
        // required by JPA
    }

    private AppUser(String username, String passwordHash, Role role, UUID practitionerId) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.practitionerId = practitionerId;
        this.active = true;
        this.mustChangePassword = true;
    }

    /**
     * A new, active user. The id is assigned here and not by the database (D-19).
     *
     * @param passwordHash the password already hashed: a user never holds the password itself
     * @param practitionerId required for a practitioner, and refused for any other role
     * @throws IllegalArgumentException when a value is missing or malformed
     */
    public static AppUser create(String username, String passwordHash, Role role, UUID practitionerId) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("A username is 3 to " + MAX_USERNAME_LENGTH
                    + " lower-case letters, digits, dots, hyphens or underscores");
        }
        requireHash(passwordHash);
        if (role == null) {
            throw new IllegalArgumentException("A user needs a role");
        }
        if (role == Role.PRACTITIONER && practitionerId == null) {
            throw new IllegalArgumentException("A practitioner's user needs the practitioner they stand for");
        }
        if (role != Role.PRACTITIONER && practitionerId != null) {
            throw new IllegalArgumentException("Only a practitioner's user stands for a practitioner");
        }
        return new AppUser(username, passwordHash, role, practitionerId);
    }

    /** The entry that records this user's creation. */
    public AppUserChange created(LocalDateTime at, String by) {
        return new AppUserChange(id, UserAction.CREATED, at, by);
    }

    /** @throws UserStatusException when the user is inactive already */
    public AppUserChange deactivate(LocalDateTime at, String by) {
        if (!active) {
            throw new UserStatusException("The user is inactive already");
        }
        this.active = false;
        return new AppUserChange(id, UserAction.DEACTIVATED, at, by);
    }

    /** @throws UserStatusException when the user is active already */
    public AppUserChange reactivate(LocalDateTime at, String by) {
        if (active) {
            throw new UserStatusException("The user is active already");
        }
        this.active = true;
        return new AppUserChange(id, UserAction.REACTIVATED, at, by);
    }

    /**
     * An admin replaces the password, whatever it was: the user forgot it, or it is to be changed. The
     * new one is temporary, as the one the user was created with.
     */
    public AppUserChange resetPassword(String passwordHash, LocalDateTime at, String by) {
        requireHash(passwordHash);
        this.passwordHash = passwordHash;
        this.mustChangePassword = true;
        return new AppUserChange(id, UserAction.PASSWORD_RESET, at, by);
    }

    /**
     * The user changes their own password, which is theirs alone from then on. Whether they gave the
     * current one, and whether the new one differs, is checked against the hashes by the service.
     */
    public AppUserChange changePassword(String passwordHash, LocalDateTime at) {
        requireHash(passwordHash);
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
        return new AppUserChange(id, UserAction.PASSWORD_CHANGED, at, username);
    }

    private static void requireHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("A user needs a password");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UUID getPractitionerId() {
        return practitionerId;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }
}
