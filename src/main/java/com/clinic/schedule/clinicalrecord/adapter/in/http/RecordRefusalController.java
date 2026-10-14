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

import com.clinic.schedule.clinicalrecord.application.usecase.ListRecordRefusalsUseCase;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;
import com.clinic.schedule.shared.http.ErrorResponse;

/**
 * The admins look through the refused attempts to reach clinical content (D-72). The route is theirs
 * alone (D-34). The patient is searched, and the names found, as for the readings (D-71).
 */
@RestController
class RecordRefusalController {

    private final ListRecordRefusalsUseCase refusals;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    RecordRefusalController(ListRecordRefusalsUseCase refusals, PatientReader patients,
            PractitionerReader practitioners) {
        this.refusals = refusals;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    @GetMapping("/api/record-refusals")
    RecordRefusalsResponse list(@RequestParam(required = false) String patient,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Set<PatientId> matching = patient == null || patient.isBlank() ? null
                : patients.search(patient).stream().map(found -> new PatientId(found.id())).collect(Collectors.toSet());
        ListRecordRefusalsUseCase.Result result = refusals.handle(
                new ListRecordRefusalsUseCase.Command(matching, user, from, to));

        Map<UUID, PatientSummary> patientsById = patients.findAll(result.refusals().stream()
                        .flatMap(refusal -> refusal.patientId().stream())
                        .map(PatientId::value)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        return RecordRefusalsResponse.of(result, patientsById, names);
    }

    /** The first day after the last. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> malformedRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e));
    }
}
