package com.clinic.schedule.patient.api;

import java.util.UUID;

/**
 * A patient as other modules see them: enough to recognise them on a screen. {@code document}
 * is a ready-to-read label such as "DNI 30111222", or null for a patient without one.
 */
public record PatientSummary(UUID id, String fullName, String document) {
}
