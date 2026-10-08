package com.clinic.schedule.portal.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The patient a web account would be opened for, as much as opening one needs to know (D-79), and booking for
 * them on the web: whether they registered themselves there (D-91).
 *
 * @param birthDate null when nobody recorded it
 * @param registeredOnWeb whether they registered themselves on the web rather than at reception
 */
public record AccountHolder(UUID patientId, String fullName, boolean identityVerified, LocalDate birthDate,
        boolean registeredOnWeb) {

    /** Registered on the web and not seen at reception yet: nobody knows they are who they said (D-91). */
    public boolean awaitsVerification() {
        return registeredOnWeb && !identityVerified;
    }
}
