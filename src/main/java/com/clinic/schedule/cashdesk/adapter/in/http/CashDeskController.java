package com.clinic.schedule.cashdesk.adapter.in.http;

import java.time.LocalDate;
import java.util.List;
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
import com.clinic.schedule.cashdesk.application.usecase.CoverVisitUseCase;
import com.clinic.schedule.cashdesk.application.usecase.RefundVisitUseCase;
import com.clinic.schedule.cashdesk.application.usecase.ViewCashDeskDayUseCase;
import com.clinic.schedule.cashdesk.application.usecase.WithdrawCoverageUseCase;
import com.clinic.schedule.cashdesk.domain.CashDeskDay;
import com.clinic.schedule.cashdesk.domain.Visit;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * The day's cash desk, charging a visit pending or given back whole (D-87), giving back from a charge (D-88), and
 * covering an emergency's pending visit or withdrawing a coverage marked by mistake (D-90). Two routes read the
 * day, one per role, each with an answer of its own, so what each role sees is decided by the route (D-34):
 * {@code /api/cash-desk} is reception's, whole; {@code /api/cash-desk-log} is the admins', without the patients,
 * the refunds' reasons, the pending visits and the covered ones.
 */
@RestController
class CashDeskController {

    private final ViewCashDeskDayUseCase view;
    private final ChargeVisitUseCase charge;
    private final RefundVisitUseCase refund;
    private final CoverVisitUseCase cover;
    private final WithdrawCoverageUseCase withdraw;
    private final PatientReader patients;

    CashDeskController(ViewCashDeskDayUseCase view, ChargeVisitUseCase charge, RefundVisitUseCase refund,
            CoverVisitUseCase cover, WithdrawCoverageUseCase withdraw, PatientReader patients) {
        this.view = view;
        this.charge = charge;
        this.refund = refund;
        this.cover = cover;
        this.withdraw = withdraw;
        this.patients = patients;
    }

    @GetMapping("/api/cash-desk")
    CashDeskResponse forReception(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CashDeskDay day = view.handle(date);
        Map<UUID, PatientSummary> patientsById = patients.findAll(Stream.of(day.visits(), day.pending(), day.covered())
                        .flatMap(List::stream).map(Visit::patientId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        return CashDeskResponse.of(day, patientsById);
    }

    @GetMapping("/api/cash-desk-log")
    CashDeskLogResponse forAdmins(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return CashDeskLogResponse.of(view.handle(date));
    }

    /**
     * An emergency's pending charge, or a visit given back whole charged again; the answer is the day's cash
     * desk, today's.
     */
    @PostMapping("/api/visits/{id}/charge")
    CashDeskResponse charge(@PathVariable UUID id, @RequestBody ChargeRequest request) {
        charge.handle(new ChargeVisitUseCase.Command(id, request.amount(), request.means()));
        return forReception(null);
    }

    /** Some or all of what is left of the visit's charge, given back; the answer is today's cash desk. */
    @PostMapping("/api/visits/{id}/refund")
    CashDeskResponse refund(@PathVariable UUID id, @RequestBody RefundRequest request) {
        refund.handle(new RefundVisitUseCase.Command(id, request.amount(), request.reason()));
        return forReception(null);
    }

    /** An emergency's pending visit, covered, with its copay if any; the answer is today's cash desk. */
    @PostMapping("/api/visits/{id}/coverage")
    CashDeskResponse cover(@PathVariable UUID id, @RequestBody CoverRequest request) {
        cover.handle(new CoverVisitUseCase.Command(id, request.token(), request.copay(), request.means()));
        return forReception(null);
    }

    /** A coverage marked by mistake, withdrawn; the answer is today's cash desk. */
    @PostMapping("/api/visits/{id}/coverage/withdrawal")
    CashDeskResponse withdraw(@PathVariable UUID id, @RequestBody WithdrawalRequest request) {
        withdraw.handle(new WithdrawCoverageUseCase.Command(id, request.reason()));
        return forReception(null);
    }
}
