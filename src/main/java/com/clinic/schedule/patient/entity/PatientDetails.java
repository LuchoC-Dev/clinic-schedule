package com.clinic.schedule.patient.entity;

import java.time.LocalDate;

/**
 * What the practice knows about a patient besides who they are (D-73): when they were born and how
 * to reach them. All of it is optional. Who the patient is — their name and document — is their
 * identity, changed apart and kept in a history of its own (D-60).
 *
 * @param birthDate null when not given
 * @param phone null when not given
 * @param email null when not given
 */
public record PatientDetails(LocalDate birthDate, Phone phone, Email email) {

    /** Nothing known yet: the patients registered before any of it was asked, and most at first. */
    public static final PatientDetails NONE = new PatientDetails(null, null, null);
}
