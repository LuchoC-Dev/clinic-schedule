package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.clinicalrecord.application.usecase.ListRecordReadingsUseCase;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;
import com.clinic.schedule.shared.http.ErrorResponse;

/**
 * The admins look through the readings of clinical records (D-71). The route is theirs alone (D-34).
 *
 * <p>The patient is searched as reception searches — part of a name, the document or an alias — in the
 * patient module, and the names come from that module and the practitioner module (D-51): the use case
 * knows only ids.
 */
@RestController
class RecordReadingController {

    private final ListRecordReadingsUseCase readings;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    RecordReadingController(ListRecordReadingsUseCase readings, PatientReader patients,
            PractitionerReader practitioners) {
        this.readings = readings;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    @GetMapping("/api/record-readings")
    RecordReadingsResponse list(@RequestParam(required = false) String patient,
            @RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Set<PatientId> matching = patient == null || patient.isBlank() ? null
                : patients.search(patient).stream().map(found -> new PatientId(found.id())).collect(Collectors.toSet());
        ListRecordReadingsUseCase.Result result = readings.handle(new ListRecordReadingsUseCase.Command(matching,
                practitionerId == null ? null : new PractitionerId(practitionerId), from, to));

        Map<UUID, PatientSummary> patientsById = patients.findAll(result.readings().stream()
                        .map(reading -> reading.patientId().value())
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        return RecordReadingsResponse.of(result, patientsById, names);
    }

    /** The first day after the last. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> malformedRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e.getMessage()));
    }
}
