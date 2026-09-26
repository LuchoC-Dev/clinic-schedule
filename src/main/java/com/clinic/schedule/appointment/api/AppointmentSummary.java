package com.clinic.schedule.appointment.api;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An appointment as other modules see it: who, with whom, when, whether the patient arrived, and
 * whether they were absent (D-61).
 */
public record AppointmentSummary(UUID id, UUID patientId, UUID practitionerId, LocalDateTime startsAt,
        boolean checkedIn, boolean absent) {
}
