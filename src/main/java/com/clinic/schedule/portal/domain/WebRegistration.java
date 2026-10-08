package com.clinic.schedule.portal.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A registration made on the web, waiting to be confirmed from the link sent to its email (D-91): until then
 * nobody is a patient, nor has an account. Only the hash of the link's {@link SecretToken} is kept, as an
 * activation's (D-79), and the password the adult chose already hashed. Its document need not be unique among
 * the registrations waiting: whoever confirms first is registered. One never confirmed is forgotten once it
 * expires.
 *
 * <p>Made by {@link WebRegistrationPolicy} alone, which checks who may register.
 *
 * @param hash the SHA-256 of the token, in hexadecimal
 * @param email the account's, in lower case
 * @param passwordHash the password chosen, already hashed
 */
public record WebRegistration(UUID id, String hash, Registrant registrant, String email, String passwordHash,
        LocalDateTime requestedAt, LocalDateTime expiresAt) {

    public WebRegistration {
        if (id == null || hash == null || registrant == null || email == null || passwordHash == null
                || requestedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("A registration needs who, its email, its password, its hash and its times");
        }
    }

    /** The token, to go in the link and nowhere else, and what is kept of it. */
    public record Issued(String token, WebRegistration stored) {
    }

    static Issued issue(Registrant registrant, String email, String passwordHash, LocalDateTime now,
            Duration validFor) {
        String token = SecretToken.generate();
        return new Issued(token, new WebRegistration(UUID.randomUUID(), hashOf(token), registrant, email, passwordHash,
                now, now.plus(validFor)));
    }

    /** What is kept of a token, and what a link is looked up by. */
    public static String hashOf(String token) {
        return SecretToken.hashOf(token);
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
