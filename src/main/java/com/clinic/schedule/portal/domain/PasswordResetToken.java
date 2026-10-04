package com.clinic.schedule.portal.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What lets a patient choose a new password from the link in an email (D-86): a {@link SecretToken}, of which
 * only the hash is kept, as the activation's (D-79). One per account: a new one replaces the last, and using it
 * ends it. When it was issued is kept too, so a link is not sent again too soon.
 *
 * @param hash the SHA-256 of the token, in hexadecimal
 */
public record PasswordResetToken(UUID accountId, String hash, LocalDateTime issuedAt, LocalDateTime expiresAt) {

    public PasswordResetToken {
        if (accountId == null || hash == null || issuedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("A link to reset a password needs its account, its hash and its times");
        }
    }

    /** The token, to go in the link and nowhere else, and what is kept of it. */
    public record Issued(String token, PasswordResetToken stored) {
    }

    public static Issued issue(UUID accountId, LocalDateTime now, Duration validFor) {
        String token = SecretToken.generate();
        return new Issued(token, new PasswordResetToken(accountId, hashOf(token), now, now.plus(validFor)));
    }

    /** What is kept of a token, and what a link is looked up by. */
    public static String hashOf(String token) {
        return SecretToken.hashOf(token);
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    /** Whether it went out less than {@code every} ago, so another is not sent yet. */
    public boolean isRecentAt(LocalDateTime now, Duration every) {
        return now.isBefore(issuedAt.plus(every));
    }
}
