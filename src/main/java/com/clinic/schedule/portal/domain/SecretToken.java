package com.clinic.schedule.portal.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The secret a link in an email carries (D-79, D-83): 256 random bits, written so they fit an address, of which
 * only a SHA-256 is kept — enough for something this unguessable, and what the link is looked up by.
 */
public final class SecretToken {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretToken() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** What is kept of a token, in hexadecimal. */
    public static String hashOf(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }
}
