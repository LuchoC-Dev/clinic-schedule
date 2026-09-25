package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.clinicalrecord.application.usecase.AddAddendumUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RecordConsultationUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.ViewClinicalRecordUseCase;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
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
    private final AddAddendumUseCase addAddendum;
    private final ViewClinicalRecordUseCase clinicalRecord;
    private final PractitionerReader practitioners;

    ConsultationController(RecordConsultationUseCase record, AddAddendumUseCase addAddendum,
            ViewClinicalRecordUseCase clinicalRecord, PractitionerReader practitioners) {
        this.record = record;
        this.addAddendum = addAddendum;
        this.clinicalRecord = clinicalRecord;
        this.practitioners = practitioners;
    }

    /** Records a consultation. A retry with the same id answers the same one, still 201. */
    @PostMapping("/api/consultations")
    ResponseEntity<ConsultationResponse> record(@Valid @RequestBody RecordConsultationRequest request) {
        RecordConsultationUseCase.Recorded recorded = record.handle(request.toCommand());
        return ResponseEntity
                .created(URI.create("/api/consultations/" + recorded.consultation().id().value()))
                .body(ConsultationResponse.of(recorded.consultation(), recorded.checks(), recorded.orders(),
                        practitionerNames()));
    }

    /**
     * Adds an addendum to a consultation already sent, answering the consultation with it. A
     * business operation on the consultation, so a {@code POST} on a sub-path (D-23, D-52).
     */
    @PostMapping("/api/consultations/{id}/addenda")
    ResponseEntity<ConsultationResponse> addAddendum(@PathVariable UUID id,
            @Valid @RequestBody AddAddendumRequest request) {
        AddAddendumUseCase.Added added = addAddendum.handle(request.toCommand(id));
        return ResponseEntity
                .created(URI.create("/api/consultations/" + id))
                .body(ConsultationResponse.of(added.consultation(), added.checks(), added.orders(), practitionerNames()));
    }

    /**
     * The patient's consultations, each with its checks, and the checks done without one, from
     * every practitioner, newest first; 403 without access (D-49, D-53).
     */
    @GetMapping("/api/clinical-records/{patientId}")
    ClinicalRecordResponse clinicalRecord(@PathVariable UUID patientId) {
        return ClinicalRecordResponse.of(clinicalRecord.handle(new PatientId(patientId)), practitionerNames());
    }

    private Map<UUID, String> practitionerNames() {
        return practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
    }
}
