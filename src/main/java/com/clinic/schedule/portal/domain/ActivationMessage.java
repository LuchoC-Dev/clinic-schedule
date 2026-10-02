package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;

/**
 * The email that lets a patient activate their account (D-79): to the account's address, naming the
 * patient, with the token for the link and when it expires. How the link is written is the sender's.
 */
public record ActivationMessage(String address, String fullName, String token, LocalDateTime expiresAt) {
}
