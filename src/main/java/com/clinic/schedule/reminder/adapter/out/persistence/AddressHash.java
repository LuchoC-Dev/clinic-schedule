package com.clinic.schedule.reminder.adapter.out.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * The hash a reminder keeps of the address it went to, instead of the address (D-78): bcrypt, salted
 * and slow on purpose, so guessing addresses one by one against it costs a great deal for each row; over
 * the address's SHA-256, since bcrypt reads only 72 bytes and an email may be 254 characters long. It
 * answers whether a reminder went to a given address — the SHA-256 of that address, as the patient's email
 * keeps it, in lower case, matched against it by bcrypt — and nothing more.
 */
final class AddressHash {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private AddressHash() {
    }

    static String of(String address) {
        return BCRYPT.encode(sha256(address));
    }

    private static String sha256(String address) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(address.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java has SHA-256", e);
        }
    }
}
