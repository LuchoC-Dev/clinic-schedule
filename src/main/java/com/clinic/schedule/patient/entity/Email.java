package com.clinic.schedule.patient.entity;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An email to reach the patient at, and whose it is (D-73).
 *
 * <p>Only its form is checked — something, an at sign, a domain with a dot — since nothing short of
 * writing to it says it exists. It is kept in lower case, so the same address typed twice is the
 * same. It is not unique among patients: two brothers may carry their mother's.
 *
 * @param owner the person it belongs to, or null when it is the patient's own
 */
public record Email(String address, Responsible owner) {

    private static final Pattern FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public Email {
        address = address == null ? "" : address.strip().toLowerCase(Locale.ROOT);
        if (address.length() > MAX_LENGTH) {
            throw new InvalidPatientDetailsException("An email is at most " + MAX_LENGTH + " characters");
        }
        if (!FORMAT.matcher(address).matches()) {
            throw new InvalidPatientDetailsException("An email looks like name@domain.com");
        }
    }

    /** The longest address the standards allow, and the length of the column. */
    static final int MAX_LENGTH = 254;

    /**
     * The email, or none when no address was given.
     *
     * @throws InvalidPatientDetailsException when a person is given without an address
     */
    public static Email ofNullable(String address, Responsible owner) {
        if (address == null || address.isBlank()) {
            if (owner != null) {
                throw new InvalidPatientDetailsException("An email's owner needs the email");
            }
            return null;
        }
        return new Email(address, owner);
    }
}
