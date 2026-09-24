package com.clinic.schedule.appointment.api;

import java.time.LocalDateTime;
import java.util.UUID;

/** An appointment as other modules see it: who, with whom, when, and whether the patient arrived. */
public record AppointmentSummary(UUID id, UUID patientId, UUID practitionerId, LocalDateTime startsAt,
        boolean checkedIn) {
}
