package com.clinic.schedule.consultation.adapter.in.http;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.consultation.application.usecase.RecordConsultationUseCase;
import com.clinic.schedule.consultation.application.usecase.ViewClinicalRecordUseCase;
import com.clinic.schedule.consultation.domain.Consultation;
import com.clinic.schedule.consultation.domain.PatientId;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

import jakarta.validation.Valid;

/**
 * Consultations and clinical records, for practitioners only (D-51). Thin: translate, call the
 * use case, translate back. Who may read or write what is the use cases' decision.
 *
 * <p>The record has its own route rather than one under {@code /api/patients}: a route under
 * another module's resource would hide who answers it (D-32). The practitioners' names are read
 * here, from their module's api, because only the answer needs them (D-33).
 */
@RestController
class ConsultationController {

    private final RecordConsultationUseCase record;
    private final ViewClinicalRecordUseCase clinicalRecord;
    private final PractitionerReader practitioners;

    ConsultationController(RecordConsultationUseCase record, ViewClinicalRecordUseCase clinicalRecord,
            PractitionerReader practitioners) {
        this.record = record;
        this.clinicalRecord = clinicalRecord;
        this.practitioners = practitioners;
    }

    /** Records a consultation. A retry with the same id answers the same one, still 201. */
    @PostMapping("/api/consultations")
    ResponseEntity<ConsultationResponse> record(@Valid @RequestBody RecordConsultationRequest request) {
        Consultation recorded = record.handle(request.toCommand());
        return ResponseEntity
                .created(URI.create("/api/consultations/" + recorded.id().value()))
                .body(ConsultationResponse.of(recorded, practitionerNames().get(recorded.practitionerId().value())));
    }

    /** The patient's consultations from every practitioner, newest first; 403 without access (D-49). */
    @GetMapping("/api/clinical-records/{patientId}")
    List<ConsultationResponse> clinicalRecord(@PathVariable UUID patientId) {
        List<Consultation> consultations = clinicalRecord.handle(new PatientId(patientId)).consultations();
        Map<UUID, String> names = practitionerNames();
        return consultations.stream()
                .map(consultation -> ConsultationResponse.of(consultation,
                        names.get(consultation.practitionerId().value())))
                .toList();
    }

    private Map<UUID, String> practitionerNames() {
        return practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
    }
}
