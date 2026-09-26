package com.clinic.schedule.patient.api;

import java.util.UUID;

/**
 * A patient as other modules see them: enough to recognise them on a screen. {@code document}
 * is a ready-to-read label such as "DNI 30111222", or null for a patient without one.
 * {@code fullName} is the alias of a patient who gave no name, and {@code identityVerified} says
 * whether reception saw their document, so a screen can warn when nobody has (D-60).
 */
public record PatientSummary(UUID id, String fullName, String document, boolean identityVerified) {
}
