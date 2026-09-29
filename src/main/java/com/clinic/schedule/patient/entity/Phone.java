package com.clinic.schedule.patient.entity;

import java.util.regex.Pattern;

/**
 * A phone to reach the patient on, and whose it is (D-73).
 *
 * <p>It normalizes what reception types, as the document does: spaces, dashes, dots and brackets
 * carry no meaning, so "(011) 4555-6666" and "01145556666" are the same number. What remains is
 * digits, with an optional leading {@code +}, 8 to 15 of them — 15 is the longest an international
 * number may be. Whether it takes WhatsApp is left for the reminders.
 *
 * @param owner the person it belongs to, or null when it is the patient's own
 */
public record Phone(String number, Responsible owner) {

    private static final Pattern FORMAT = Pattern.compile("\\+?\\d{8,15}");

    public Phone {
        number = number == null ? "" : number.strip().replaceAll("[\\s.()-]", "");
        if (!FORMAT.matcher(number).matches()) {
            throw new InvalidPatientDetailsException(
                    "A phone number has 8 to 15 digits, with an optional leading +");
        }
    }

    /**
     * The phone, or none when no number was given.
     *
     * @throws InvalidPatientDetailsException when a person is given without a number
     */
    public static Phone ofNullable(String number, Responsible owner) {
        if (number == null || number.isBlank()) {
            if (owner != null) {
                throw new InvalidPatientDetailsException("A phone's owner needs the phone number");
            }
            return null;
        }
        return new Phone(number, owner);
    }
}
