package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.clinicalrecord.application.usecase.ListPendingChecksUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RecordCheckResultUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RecordCheckUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RequestAccessUseCase;
import com.clinic.schedule.clinicalrecord.domain.AccessGrant;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

import jakarta.validation.Valid;

/**
 * Checks, for practitioners only (D-51, D-53, D-54): one done without a consultation, the result of
 * a pending one, the list of those pending, and access to a record to report one. Checks done
 * during a consultation are sent with it. Thin, like every controller:
 * who may do what is the use cases' decision.
 */
@RestController
class CheckController {

    private final RecordCheckUseCase recordCheck;
    private final RecordCheckResultUseCase recordResult;
    private final ListPendingChecksUseCase pendingChecks;
    private final RequestAccessUseCase requestAccess;
    private final PractitionerReader practitioners;
    private final PatientReader patients;

    CheckController(RecordCheckUseCase recordCheck, RecordCheckResultUseCase recordResult,
            ListPendingChecksUseCase pendingChecks, RequestAccessUseCase requestAccess,
            PractitionerReader practitioners, PatientReader patients) {
        this.recordCheck = recordCheck;
        this.recordResult = recordResult;
        this.pendingChecks = pendingChecks;
        this.requestAccess = requestAccess;
        this.practitioners = practitioners;
        this.patients = patients;
    }

    /** A check done without a consultation, with its result or pending. A retry answers the same one, still 201. */
    @PostMapping("/api/checks")
    ResponseEntity<CheckResponse> record(@Valid @RequestBody RecordCheckRequest request) {
        Check recorded = recordCheck.handle(request.toCommand());
        return ResponseEntity
                .created(URI.create("/api/checks/" + recorded.id().value()))
                .body(CheckResponse.of(recorded, practitionerNames()));
    }

    /**
     * The result of a pending check, by any practitioner who may read the patient's record. A
     * business operation on the check, so a {@code POST} on a sub-path (D-23); a second result is a 409.
     */
    @PostMapping("/api/checks/{id}/result")
    CheckResponse recordResult(@PathVariable UUID id, @Valid @RequestBody RecordCheckResultRequest request) {
        Check reported = recordResult.handle(new RecordCheckResultUseCase.Command(new CheckId(id), request.text()));
        return CheckResponse.of(reported, practitionerNames());
    }

    /**
     * Every check of the practice waiting for its result, the oldest first: which patient and which
     * check, nothing else of the record, and whether the user may already read it (D-54).
     */
    @GetMapping("/api/checks/pending")
    List<PendingCheckResponse> pending() {
        List<ListPendingChecksUseCase.PendingCheck> found = pendingChecks.handle();
        Map<UUID, PatientSummary> patientsById = patients.findAll(found.stream()
                        .map(pending -> pending.check().patientId().value())
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<UUID, String> names = practitionerNames();
        return found.stream().map(pending -> PendingCheckResponse.of(pending, patientsById, names)).toList();
    }

    /**
     * Access to the patient's record, to report this pending check. Granted at once, for now, and
     * recorded (D-54): 201 with the grant; 404 for no such check, 409 when it already has its result.
     */
    @PostMapping("/api/checks/{id}/access-grants")
    ResponseEntity<AccessGrantResponse> requestAccess(@PathVariable UUID id,
            @Valid @RequestBody RequestAccessRequest request) {
        AccessGrant grant = requestAccess.handle(new RequestAccessUseCase.Command(new CheckId(id), request.reason()));
        return ResponseEntity
                .created(URI.create("/api/clinical-records/" + grant.patientId().value()))
                .body(AccessGrantResponse.of(grant, practitionerNames()));
    }

    private Map<UUID, String> practitionerNames() {
        return practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
    }
}
