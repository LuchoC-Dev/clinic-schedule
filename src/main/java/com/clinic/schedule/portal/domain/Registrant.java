package com.clinic.schedule.portal.domain;

import java.time.LocalDate;

/**
 * Who registers themselves on the web (D-91), as they typed it: whether it can be a patient is the patient
 * module's to say, and it is not one until they confirm it. The document's number and the phone are kept with
 * whatever separators were typed, up to {@value #MAX_TYPED} characters: longer, they cannot be one.
 *
 * @param documentType such as "DNI"
 * @param phoneNumber null when not given
 */
public record Registrant(String fullName, String documentType, String documentNumber, LocalDate birthDate,
        String phoneNumber) {

    /** As long as the columns that keep them while the registration waits. */
    static final int MAX_TYPED = 40;

    public Registrant {
        if (documentNumber != null && documentNumber.length() > MAX_TYPED
                || phoneNumber != null && phoneNumber.length() > MAX_TYPED) {
            throw new IllegalArgumentException("A document's number or a phone is at most " + MAX_TYPED + " characters");
        }
    }
}
