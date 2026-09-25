package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.application.usecase.ListPendingChecksUseCase;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * A check waiting for its result, as the list of pending results shows it: which patient, which
 * check, when and by whom — nothing else of the record — and whether the user may already read the
 * record or has to ask for access (D-54).
 */
record PendingCheckResponse(
        UUID checkId,
        UUID patientId,
        String patientName,
        String patientDocument,
        String description,
        LocalDateTime performedAt,
        String performedByName,
        boolean mayRead) {

    static PendingCheckResponse of(ListPendingChecksUseCase.PendingCheck pending, Map<UUID, PatientSummary> patients,
            Map<UUID, String> names) {
        Check check = pending.check();
        PatientSummary patient = patients.get(check.patientId().value());
        return new PendingCheckResponse(
                check.id().value(),
                check.patientId().value(),
                patient == null ? null : patient.fullName(),
                patient == null ? null : patient.document(),
                check.description(),
                check.performedAt(),
                names.get(check.performedBy().value()),
                pending.mayRead());
    }
}
