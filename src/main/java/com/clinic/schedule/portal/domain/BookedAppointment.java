package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/** An appointment still booked — neither cancelled nor absent —, as cancelling it needs it (D-83). */
public record BookedAppointment(UUID id, UUID patientId, UUID practitionerId, LocalDateTime startsAt) {
}
