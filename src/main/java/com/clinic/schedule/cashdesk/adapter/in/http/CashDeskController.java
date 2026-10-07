package com.clinic.schedule.cashdesk.adapter.in.http;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.cashdesk.application.usecase.ChargeVisitUseCase;
import com.clinic.schedule.cashdesk.application.usecase.ViewCashDeskDayUseCase;
import com.clinic.schedule.cashdesk.domain.CashDeskDay;
import com.clinic.schedule.cashdesk.domain.Visit;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * The day's cash desk, and charging an emergency's pending visit (D-87). Two routes read the day, one per
 * role, each with an answer of its own, so what each role sees is decided by the route (D-34):
 * {@code /api/cash-desk} is reception's, whole; {@code /api/cash-desk-log} is the admins', without the
 * patients and the pending visits.
 */
@RestController
class CashDeskController {

    private final ViewCashDeskDayUseCase view;
    private final ChargeVisitUseCase charge;
    private final PatientReader patients;

    CashDeskController(ViewCashDeskDayUseCase view, ChargeVisitUseCase charge, PatientReader patients) {
        this.view = view;
        this.charge = charge;
        this.patients = patients;
    }

    @GetMapping("/api/cash-desk")
    CashDeskResponse forReception(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CashDeskDay day = view.handle(date);
        Map<UUID, PatientSummary> patientsById = patients.findAll(Stream.concat(day.charged().stream(),
                        day.pending().stream()).map(Visit::patientId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        return CashDeskResponse.of(day, patientsById);
    }

    @GetMapping("/api/cash-desk-log")
    CashDeskLogResponse forAdmins(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return CashDeskLogResponse.of(view.handle(date));
    }

    /** An emergency's pending charge, recorded; the answer is the day's cash desk, today's. */
    @PostMapping("/api/visits/{id}/charge")
    CashDeskResponse charge(@PathVariable UUID id, @RequestBody ChargeRequest request) {
        charge.handle(new ChargeVisitUseCase.Command(id, request.amount()));
        return forReception(null);
    }
}
