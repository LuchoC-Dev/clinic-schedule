package com.clinic.schedule.clinicalrecord.domain;

import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The code printed on every page of a copy of a record, to check it against the application (D-95): twelve
 * characters of Crockford's base 32 — sixty random bits — shown in three groups, {@code K7QM-3XPA-9TRD}. Its
 * alphabet has no I, L, O nor U, so whoever types it from paper reads an I or an L as a 1 and an O as a 0.
 */
public record CopyCode(String value) {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int LENGTH = 12;

    public CopyCode {
        if (value == null || value.length() != LENGTH || !value.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)) {
            throw new IllegalArgumentException("A copy's code is " + LENGTH + " characters of Crockford's base 32");
        }
    }

    /** A new code, from a source of randomness that cannot be guessed. */
    public static CopyCode generate(RandomGenerator random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new CopyCode(code.toString());
    }

    /** The code as someone types it — with dashes or spaces, in lower case, an O for a 0 —, or empty when it is none. */
    public static Optional<CopyCode> read(String typed) {
        if (typed == null) {
            return Optional.empty();
        }
        String code = typed.replaceAll("[\s-]", "").toUpperCase()
                .replace('O', '0').replace('I', '1').replace('L', '1');
        try {
            return Optional.of(new CopyCode(code));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** In three groups of four, as it is printed. */
    public String formatted() {
        return value.substring(0, 4) + "-" + value.substring(4, 8) + "-" + value.substring(8);
    }
}
