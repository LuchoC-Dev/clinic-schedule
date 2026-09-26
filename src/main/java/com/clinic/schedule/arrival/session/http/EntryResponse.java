package com.clinic.schedule.arrival.session.http;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.arrival.session.domain.EntryState;

/** A patient's place in a queue session, as the API returns it (D-63). */
record EntryResponse(UUID id, UUID patientId, LocalDateTime arrivedAt, boolean emergency, EntryState state,
        int timesCalled, UUID withPractitionerId) {

    static EntryResponse of(Entry entry) {
        return new EntryResponse(entry.id().value(), entry.patientId(), entry.arrivedAt(), entry.isEmergency(),
                entry.state(), entry.timesCalled(), entry.withPractitioner());
    }
}
