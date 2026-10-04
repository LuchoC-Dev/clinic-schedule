package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;

/**
 * The email with the link to choose a new password (D-86): to the account's address, naming the patient, with
 * the token for the link and when it expires. How the link is written is the sender's.
 */
public record PasswordResetMessage(String address, String fullName, String token, LocalDateTime expiresAt) {
}
