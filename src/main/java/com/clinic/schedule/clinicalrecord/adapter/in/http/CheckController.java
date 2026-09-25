package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.clinicalrecord.application.usecase.RecordCheckResultUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RecordCheckUseCase;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

import jakarta.validation.Valid;

/**
 * Checks, for practitioners only (D-51, D-53): one done without a consultation, and the result of
 * a pending one. Checks done during a consultation are sent with it. Thin, like every controller:
 * who may do what is the use cases' decision.
 */
@RestController
class CheckController {

    private final RecordCheckUseCase recordCheck;
    private final RecordCheckResultUseCase recordResult;
    private final PractitionerReader practitioners;

    CheckController(RecordCheckUseCase recordCheck, RecordCheckResultUseCase recordResult,
            PractitionerReader practitioners) {
        this.recordCheck = recordCheck;
        this.recordResult = recordResult;
        this.practitioners = practitioners;
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

    private Map<UUID, String> practitionerNames() {
        return practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
    }
}
