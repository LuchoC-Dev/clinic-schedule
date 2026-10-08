package com.clinic.schedule.patient.api;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * A patient as other modules see them: enough to recognise them on a screen. {@code document}
 * is a ready-to-read label such as "DNI 30111222", or null for a patient without one.
 * {@code fullName} is the alias of a patient who gave no name, and {@code identityVerified} says
 * whether reception saw their document, so a screen can warn when nobody has (D-60).
 *
 * <p>{@code birthDate} and {@code insurance} are null when not given. They are here so a practitioner
 * reads the patient's age and health insurance — its member number, for a prescription — beside the
 * clinical record (D-73, D-74); how to reach the patient is not, since only reception reads it.
 *
 * <p>{@code registeredOnWeb} says the patient registered themselves on the web rather than at reception, which
 * stays so after reception verifies them (D-91).
 */
public record PatientSummary(UUID id, String fullName, String document, boolean identityVerified,
        LocalDate birthDate, Insurance insurance, boolean registeredOnWeb) {

    /** The insurer, and the member number and plan, each null when not given. */
    public record Insurance(String insurer, String memberNumber, String plan) {
    }

    /** The patient's age on that day, or null without a date of birth. */
    public Period ageOn(LocalDate day) {
        return birthDate == null ? null : Period.between(birthDate, day);
    }
}
