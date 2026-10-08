package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;

/**
 * The email that lets an adult confirm the registration they made on the web (D-91): to the address they gave,
 * naming them as they typed it, with the token for the link and when it expires. How the link is written is the
 * sender's.
 */
public record RegistrationMessage(String address, String fullName, String token, LocalDateTime expiresAt) {
}
