package com.clinic.schedule.user.api;

import java.util.UUID;

/**
 * A user as the security module needs it to log them in: who they are, how to check their
 * password, their role, and the practitioner they stand for — null for reception.
 */
public record UserAccount(String username, String passwordHash, Role role, UUID practitionerId) {
}
