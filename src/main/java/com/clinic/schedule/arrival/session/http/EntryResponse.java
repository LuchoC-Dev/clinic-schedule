package com.clinic.schedule.arrival.session.http;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.arrival.session.domain.EntryState;

/**
 * A patient's place in a queue session, as the API returns it (D-63, D-64): the appointment they came
 * with and whom they are kept for, both null when there is none.
 */
record EntryResponse(UUID id, UUID patientId, LocalDateTime arrivedAt, boolean emergency, UUID appointmentId,
        LocalDateTime appointmentAt, UUID reservedForId, EntryState state, int timesCalled, UUID withPractitionerId) {

    static EntryResponse of(Entry entry) {
        return new EntryResponse(entry.id().value(), entry.patientId(), entry.arrivedAt(), entry.isEmergency(),
                entry.appointmentId(), entry.appointmentAt(), entry.reservedFor(), entry.state(), entry.timesCalled(),
                entry.withPractitioner());
    }
}
