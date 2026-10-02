package com.clinic.schedule.portal.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * What lets a patient activate their account from the link in an email (D-79): 256 random bits, of which
 * only a SHA-256 is kept. That is enough for something this unguessable — a bcrypt, as the reminders keep of
 * an address (D-78), is for what could be guessed — and it lets the link find its account. One per account:
 * a new one replaces the last, and using it, or deactivating the account, ends it.
 *
 * @param hash the SHA-256 of the token, in hexadecimal
 */
public record ActivationToken(UUID accountId, String hash, LocalDateTime expiresAt) {

    private static final SecureRandom RANDOM = new SecureRandom();

    public ActivationToken {
        if (accountId == null || hash == null || expiresAt == null) {
            throw new IllegalArgumentException("An activation needs its account, its hash and when it expires");
        }
    }

    /** The token, to go in the link and nowhere else, and what is kept of it. */
    public record Issued(String token, ActivationToken stored) {
    }

    public static Issued issue(UUID accountId, LocalDateTime now, Duration validFor) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Issued(token, new ActivationToken(accountId, hashOf(token), now.plus(validFor)));
    }

    /** What is kept of a token, and what a link is looked up by. */
    public static String hashOf(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
