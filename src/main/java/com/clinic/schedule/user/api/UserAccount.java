package com.clinic.schedule.user.api;

import java.util.UUID;

/**
 * A user as the security module needs it to log them in: who they are, how to check their
 * password, their role, the practitioner they stand for — null for any other role —, and whether
 * their password is temporary and must be changed before anything else (D-69).
 */
public record UserAccount(String username, String passwordHash, Role role, UUID practitionerId,
        boolean mustChangePassword) {
}
