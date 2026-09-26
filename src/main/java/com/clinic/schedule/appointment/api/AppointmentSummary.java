package com.clinic.schedule.appointment.api;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An appointment as other modules see it: who, with whom, when, and whether the patient was absent
 * (D-61). Whether they arrived is not the appointment's: the queues say it (D-65).
 */
public record AppointmentSummary(UUID id, UUID patientId, UUID practitionerId, LocalDateTime startsAt,
        boolean absent) {
}
