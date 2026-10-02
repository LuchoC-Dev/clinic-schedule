package com.clinic.schedule.shared;

import java.nio.charset.StandardCharsets;

/**
 * What a password must be before it is hashed (D-68) — a user's of the practice, or a patient's of their
 * web account (D-79), hence its place outside both modules: from 10 to 72 characters, with no rule about
 * what they are — length is what makes a password hard to guess, and rules about symbols only make
 * people write them down. 72 is where BCrypt stops reading, counted in bytes: a password with
 * accents reaches it with fewer characters, and is refused rather than cut short in silence.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /** @throws IllegalArgumentException when the password is too short or too long */
    public static String check(String password) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new IllegalArgumentException("A password is at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("A password is at most " + MAX_BYTES + " characters");
        }
        return password;
    }
}
