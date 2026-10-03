package com.clinic.schedule.portal.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What lets a patient activate their account from the link in an email (D-79): a {@link SecretToken}, of which
 * only the hash is kept — enough for something this unguessable; a bcrypt, as the reminders keep of an address
 * (D-78), is for what could be guessed — and it lets the link find its account. One per account: a new one
 * replaces the last, and using it, or deactivating the account, ends it.
 *
 * @param hash the SHA-256 of the token, in hexadecimal
 */
public record ActivationToken(UUID accountId, String hash, LocalDateTime expiresAt) {

    public ActivationToken {
        if (accountId == null || hash == null || expiresAt == null) {
            throw new IllegalArgumentException("An activation needs its account, its hash and when it expires");
        }
    }

    /** The token, to go in the link and nowhere else, and what is kept of it. */
    public record Issued(String token, ActivationToken stored) {
    }

    public static Issued issue(UUID accountId, LocalDateTime now, Duration validFor) {
        String token = SecretToken.generate();
        return new Issued(token, new ActivationToken(accountId, hashOf(token), now.plus(validFor)));
    }

    /** What is kept of a token, and what a link is looked up by. */
    public static String hashOf(String token) {
        return SecretToken.hashOf(token);
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
