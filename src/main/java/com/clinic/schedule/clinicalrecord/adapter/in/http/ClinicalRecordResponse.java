package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.application.usecase.ViewClinicalRecordUseCase;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * A patient's clinical record as the API shows it: the consultations, each with the checks done
 * during it, the checks done without a consultation, the files attached to either — each saying
 * which it belongs to — and the access granted to the record to whoever (D-53, D-54, D-57).
 *
 * <p>{@code patient} is what a practitioner reads of the patient beside the record: their date of
 * birth, null when not given — never how to reach them, which is reception's alone (D-73).
 */
record ClinicalRecordResponse(Patient patient, List<ConsultationResponse> consultations, List<CheckResponse> checks,
        List<AttachmentResponse> attachments, List<AccessGrantResponse> accessGrants) {

    record Patient(LocalDate birthDate) {
    }

    static ClinicalRecordResponse of(ViewClinicalRecordUseCase.ClinicalRecord record, PatientSummary patient,
            Map<UUID, String> names) {
        return new ClinicalRecordResponse(
                new Patient(patient == null ? null : patient.birthDate()),
                record.consultations().stream()
                        .map(consultation -> ConsultationResponse.of(consultation,
                                record.checks().stream()
                                        .filter(check -> check.consultationId().equals(Optional.of(consultation.id())))
                                        .toList(),
                                record.orders().stream()
                                        .filter(order -> order.consultationId().equals(consultation.id()))
                                        .toList(),
                                names))
                        .toList(),
                record.checks().stream()
                        .filter(check -> check.consultationId().isEmpty())
                        .map((Check check) -> CheckResponse.of(check, names))
                        .toList(),
                record.attachments().stream().map(attachment -> AttachmentResponse.of(attachment, names)).toList(),
                record.grants().stream().map(grant -> AccessGrantResponse.of(grant, names)).toList());
    }
}
