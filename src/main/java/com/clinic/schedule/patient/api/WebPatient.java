package com.clinic.schedule.patient.api;

import java.time.LocalDate;

/**
 * An adult who registers themselves on the web (D-91), as the portal hands them over, in plain values (D-24): a
 * name, a document — required, since their account logs in with it —, a date of birth and, optionally, a phone of
 * their own.
 *
 * @param documentType one of {@link PatientReader#documentTypes()}
 * @param phoneNumber blank or null when not given
 */
public record WebPatient(String fullName, String documentType, String documentNumber, LocalDate birthDate,
        String phoneNumber) {
}
