package com.clinic.schedule.reminder.adapter.in.http;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;
import com.clinic.schedule.reminder.application.usecase.ListRemindersUseCase;
import com.clinic.schedule.shared.http.ErrorResponse;

/** The reminders of the appointments of some days, for reception alone, which the route decides (D-34, D-78). */
@RestController
class ReminderController {

    private final ListRemindersUseCase reminders;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    ReminderController(ListRemindersUseCase reminders, PatientReader patients, PractitionerReader practitioners) {
        this.reminders = reminders;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    /** By the appointment's day; without days, from today to as far ahead as reminders go. */
    @GetMapping("/api/reminders")
    RemindersResponse list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ListRemindersUseCase.Result result = reminders.handle(from, to);
        Map<UUID, PatientSummary> patientsById = patients.findAll(result.reminders().stream()
                        .map(reminder -> reminder.patientId())
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        return RemindersResponse.of(result, patientsById, names);
    }

    /** The first day after the last. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> malformedRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e.getMessage()));
    }
}
