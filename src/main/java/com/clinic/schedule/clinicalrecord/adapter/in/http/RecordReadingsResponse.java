package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.application.usecase.ListRecordReadingsUseCase;
import com.clinic.schedule.clinicalrecord.domain.ReadAccess;
import com.clinic.schedule.clinicalrecord.domain.RecordReading;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * The readings of clinical records as the admins get them (D-71): who, whose, when, by which access,
 * and whether it was an attached file — never which one, whose name may say what it holds, and never
 * anything that was read.
 */
record RecordReadingsResponse(LocalDate from, LocalDate to, boolean limitReached, List<Reading> readings) {

    /** {@code readBy} is null for a reading recorded before users were. */
    record Reading(LocalDateTime readAt, Practitioner practitioner, String readBy, Patient patient, ReadAccess access,
            boolean attachment) {
    }

    record Practitioner(UUID id, String fullName) {
    }

    /** {@code document} is null when the patient gave none. */
    record Patient(UUID id, String fullName, String document) {
    }

    static RecordReadingsResponse of(ListRecordReadingsUseCase.Result result, Map<UUID, PatientSummary> patients,
            Map<UUID, String> practitionerNames) {
        return new RecordReadingsResponse(result.from(), result.to(), result.limitReached(), result.readings().stream()
                .map(reading -> reading(reading, patients, practitionerNames))
                .toList());
    }

    private static Reading reading(RecordReading reading, Map<UUID, PatientSummary> patients,
            Map<UUID, String> practitionerNames) {
        UUID practitionerId = reading.practitionerId().value();
        UUID patientId = reading.patientId().value();
        PatientSummary patient = patients.get(patientId);
        return new Reading(reading.readAt(),
                new Practitioner(practitionerId, practitionerNames.get(practitionerId)),
                reading.readBy().orElse(null),
                new Patient(patientId, patient == null ? null : patient.fullName(), patient == null ? null : patient.document()),
                reading.access(),
                reading.attachmentId().isPresent());
    }
}
