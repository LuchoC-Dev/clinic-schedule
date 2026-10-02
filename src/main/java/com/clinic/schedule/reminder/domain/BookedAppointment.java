package com.clinic.schedule.reminder.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/** A booked appointment, as much as reminding its patient needs (D-78). */
public record BookedAppointment(UUID id, UUID patientId, UUID practitionerId, LocalDateTime startsAt) {
}
