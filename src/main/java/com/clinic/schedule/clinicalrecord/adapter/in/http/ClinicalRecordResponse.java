package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.application.usecase.ViewClinicalRecordUseCase;
import com.clinic.schedule.clinicalrecord.domain.Check;

/**
 * A patient's clinical record as the API shows it: the consultations, each with the checks done
 * during it, and the checks done without a consultation, both the most recent first (D-53).
 */
record ClinicalRecordResponse(List<ConsultationResponse> consultations, List<CheckResponse> checks) {

    static ClinicalRecordResponse of(ViewClinicalRecordUseCase.ClinicalRecord record, Map<UUID, String> names) {
        return new ClinicalRecordResponse(
                record.consultations().stream()
                        .map(consultation -> ConsultationResponse.of(consultation, record.checks().stream()
                                .filter(check -> check.consultationId().equals(Optional.of(consultation.id())))
                                .toList(), names))
                        .toList(),
                record.checks().stream()
                        .filter(check -> check.consultationId().isEmpty())
                        .map((Check check) -> CheckResponse.of(check, names))
                        .toList());
    }
}
