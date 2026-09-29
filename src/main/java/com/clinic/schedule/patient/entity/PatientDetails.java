package com.clinic.schedule.patient.entity;

import java.time.LocalDate;

/**
 * What the practice knows about a patient besides who they are (D-73, D-74): when they were born, how
 * to reach them and their health insurance. All of it is optional. Who the patient is — their name
 * and document — is their identity, changed apart and kept in a history of its own (D-60).
 *
 * @param birthDate null when not given
 * @param phone null when not given
 * @param email null when not given
 * @param insurance null when not given, which says nothing about whether they pay privately: the
 *        patients registered before it was asked have none either
 */
public record PatientDetails(LocalDate birthDate, Phone phone, Email email, HealthInsurance insurance) {

    /** Nothing known yet: the patients registered before any of it was asked, and most at first. */
    public static final PatientDetails NONE = new PatientDetails(null, null, null, null);
}
