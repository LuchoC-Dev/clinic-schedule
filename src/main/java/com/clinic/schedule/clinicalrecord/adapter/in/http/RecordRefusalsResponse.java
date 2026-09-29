package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.application.usecase.ListRecordRefusalsUseCase;
import com.clinic.schedule.clinicalrecord.domain.RecordRefusal;
import com.clinic.schedule.clinicalrecord.domain.RefusedAttempt;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * The refused attempts to reach clinical content as the admins get them (D-72): who, when, what they
 * tried and whose record, when known. A file is said to be one, never which (D-71).
 */
record RecordRefusalsResponse(LocalDate from, LocalDate to, boolean limitReached, List<Refusal> refusals) {

    /**
     * @param practitioner null when whoever was refused is not a practitioner
     * @param patient null when it is not known whose record it was
     * @param route only for a refused route: the method and the path
     */
    record Refusal(LocalDateTime refusedAt, String refusedBy, RecordReadingsResponse.Practitioner practitioner,
            RecordReadingsResponse.Patient patient, RefusedAttempt attempt, String route) {
    }

    static RecordRefusalsResponse of(ListRecordRefusalsUseCase.Result result, Map<UUID, PatientSummary> patients,
            Map<UUID, String> practitionerNames) {
        return new RecordRefusalsResponse(result.from(), result.to(), result.limitReached(), result.refusals().stream()
                .map(refusal -> refusal(refusal, patients, practitionerNames))
                .toList());
    }

    private static Refusal refusal(RecordRefusal refusal, Map<UUID, PatientSummary> patients,
            Map<UUID, String> practitionerNames) {
        RecordReadingsResponse.Practitioner practitioner = refusal.practitionerId()
                .map(id -> new RecordReadingsResponse.Practitioner(id.value(), practitionerNames.get(id.value())))
                .orElse(null);
        RecordReadingsResponse.Patient patient = refusal.patientId()
                .map(id -> {
                    PatientSummary found = patients.get(id.value());
                    return new RecordReadingsResponse.Patient(id.value(), found == null ? null : found.fullName(),
                            found == null ? null : found.document());
                })
                .orElse(null);
        return new Refusal(refusal.refusedAt(), refusal.refusedBy(), practitioner, patient, refusal.attempt(),
                refusal.route().orElse(null));
    }
}
