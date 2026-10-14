package com.clinic.schedule.arrival.session.http;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The names a queue's entries carry for the applications (D-102): each patient's, with their document, and the
 * practitioners' they wait for or are with, which the session itself knows only by id. Asked once for a whole
 * session, of the patient and practitioner modules, as the queue screens ask them (D-33).
 */
record Names(Map<UUID, PatientSummary> patients, Map<UUID, String> practitioners) {

    static Names of(Collection<Entry> entries, PatientReader patients, PractitionerReader practitioners) {
        return new Names(
                patients.findAll(entries.stream().map(Entry::patientId).distinct().toList()).stream()
                        .collect(Collectors.toMap(PatientSummary::id, Function.identity())),
                practitioners.findAll().stream()
                        .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName)));
    }

    PatientSummary patient(UUID id) {
        return patients.get(id);
    }

    /** Null for no one; a practitioner no longer known is said so, as on the screens. */
    String practitioner(UUID id) {
        return id == null ? null : practitioners.getOrDefault(id, "(profesional desconocido)");
    }
}
