package com.clinic.schedule.arrival.session.screen;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.arrival.session.domain.EntryState;
import com.clinic.schedule.patient.api.PatientSummary;

/** One patient of a queue as its screens show them, with names instead of ids and their state in Spanish (D-63). */
record QueueLine(UUID entryId, UUID patientId, String patient, boolean identityVerified, String arrivedAt,
        boolean emergency, String state, int timesCalled, String practitioner) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    static QueueLine of(Entry entry, Map<UUID, PatientSummary> patients, Map<UUID, String> practitioners) {
        PatientSummary patient = patients.get(entry.patientId());
        String label = patient == null ? "(paciente desconocido)"
                : patient.document() == null ? patient.fullName() : patient.fullName() + " — " + patient.document();
        return new QueueLine(entry.id().value(), entry.patientId(), label, patient != null && patient.identityVerified(),
                TIME.format(entry.arrivedAt()), entry.isEmergency(), state(entry.state()), entry.timesCalled(),
                entry.withPractitioner() == null ? null
                        : practitioners.getOrDefault(entry.withPractitioner(), "(profesional desconocido)"));
    }

    static String state(EntryState state) {
        return switch (state) {
            case WAITING -> "Esperando";
            case CALLED -> "Llamado";
            case SEEN -> "Atendido";
            case LEFT -> "Se fue";
            case NOT_SEEN -> "No atendido";
            case DID_NOT_SHOW_UP -> "No se presentó";
        };
    }
}
