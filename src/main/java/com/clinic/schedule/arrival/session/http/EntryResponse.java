package com.clinic.schedule.arrival.session.http;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.arrival.session.domain.EntryState;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * A patient's place in a queue session, as the API returns it (D-63, D-64): the appointment they came
 * with and whom they are kept for, both null when there is none; and the names the applications show (D-102) —
 * the patient's, null when the patient module no longer knows them, their document, and the practitioners'.
 */
record EntryResponse(UUID id, UUID patientId, String patient, String patientDocument, LocalDateTime arrivedAt,
        boolean emergency, UUID appointmentId, LocalDateTime appointmentAt, UUID reservedForId, String reservedFor,
        EntryState state, int timesCalled, UUID withPractitionerId, String withPractitioner) {

    static EntryResponse of(Entry entry, Names names) {
        PatientSummary patient = names.patient(entry.patientId());
        return new EntryResponse(entry.id().value(), entry.patientId(), patient == null ? null : patient.fullName(),
                patient == null ? null : patient.document(), entry.arrivedAt(), entry.isEmergency(),
                entry.appointmentId(), entry.appointmentAt(), entry.reservedFor(), names.practitioner(entry.reservedFor()),
                entry.state(), entry.timesCalled(), entry.withPractitioner(),
                names.practitioner(entry.withPractitioner()));
    }
}
