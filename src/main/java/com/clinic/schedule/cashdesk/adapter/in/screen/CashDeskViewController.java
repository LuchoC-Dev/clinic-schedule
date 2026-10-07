package com.clinic.schedule.cashdesk.adapter.in.screen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.clinic.schedule.cashdesk.application.exception.VisitNotFoundException;
import com.clinic.schedule.cashdesk.application.usecase.ChargeVisitUseCase;
import com.clinic.schedule.cashdesk.application.usecase.RefundVisitUseCase;
import com.clinic.schedule.cashdesk.application.usecase.ViewCashDeskDayUseCase;
import com.clinic.schedule.cashdesk.domain.CashDeskDay;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.Kind;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.Movement;
import com.clinic.schedule.cashdesk.domain.CashDeskDay.Total;
import com.clinic.schedule.cashdesk.domain.Visit;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * The day's cash desk (D-87, D-88). Reception's screen, {@code /cash-desk}, shows it whole: it charges the
 * pending emergencies, gives back from a charge — some or all of what is left, saying why — and charges again
 * a visit given back whole. The admins', {@code /cash-desk-log}, shows the charges, the refunds and the
 * totals without the patients, the refunds' reasons and the pending visits. Which role reaches which is the
 * route's to say (D-34).
 */
@Controller
class CashDeskViewController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ViewCashDeskDayUseCase view;
    private final ChargeVisitUseCase charge;
    private final RefundVisitUseCase refund;
    private final PatientReader patients;

    CashDeskViewController(ViewCashDeskDayUseCase view, ChargeVisitUseCase charge, RefundVisitUseCase refund,
            PatientReader patients) {
        this.view = view;
        this.charge = charge;
        this.refund = refund;
        this.patients = patients;
    }

    @GetMapping("/cash-desk")
    String forReception(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        CashDeskDay day = view.handle(date);
        Map<UUID, PatientSummary> patientsById = patients.findAll(Stream.concat(day.visits().stream(),
                        day.pending().stream()).map(Visit::patientId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        totals(day, model);
        model.addAttribute("rows", day.movements().stream().map(movement -> Row.of(movement, patientsById)).toList());
        model.addAttribute("pending", day.pending().stream()
                .map(visit -> new PendingRow(visit.id(), patientLabel(visit, patientsById),
                        TIME.format(visit.arrivedAt())))
                .toList());
        return "cash-desk";
    }

    @GetMapping("/cash-desk-log")
    String forAdmins(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        // The admins' header, which offers their screens alone (D-68).
        model.addAttribute("admin", true);
        CashDeskDay day = view.handle(date);
        totals(day, model);
        model.addAttribute("rows", day.movements().stream().map(LogRow::of).toList());
        return "cash-desk-log";
    }

    /** An emergency's pending charge, or a visit given back whole charged again; back to the day it was on. */
    @PostMapping("/cash-desk/visits/{visitId}/charge")
    String charge(@PathVariable UUID visitId, @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String date, RedirectAttributes flash) {
        return attempt(date, flash, "Cobro registrado.",
                () -> charge.handle(new ChargeVisitUseCase.Command(visitId, amount)));
    }

    /** Some or all of what is left of the visit's charge, given back; back to the day it was on. */
    @PostMapping("/cash-desk/visits/{visitId}/refund")
    String refund(@PathVariable UUID visitId, @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String reason, @RequestParam(required = false) String date,
            RedirectAttributes flash) {
        return attempt(date, flash, "Devolución registrada.",
                () -> refund.handle(new RefundVisitUseCase.Command(visitId, amount, reason)));
    }

    private static String attempt(String date, RedirectAttributes flash, String done, Runnable command) {
        try {
            command.run();
            flash.addFlashAttribute("message", done);
        } catch (ChargeRuleViolationException e) {
            flash.addFlashAttribute("error", CashDeskWords.refusal(e));
        } catch (VisitNotFoundException e) {
            flash.addFlashAttribute("error", "Esa visita no existe.");
        }
        UriComponentsBuilder back = UriComponentsBuilder.fromPath("/cash-desk");
        if (date != null && !date.isBlank()) {
            back.queryParam("date", date);
        }
        return "redirect:" + back.toUriString();
    }

    private static void totals(CashDeskDay day, Model model) {
        model.addAttribute("date", day.date());
        model.addAttribute("total", TotalRow.of("Total", day.total()));
        model.addAttribute("byRecorder", day.byRecorder().stream()
                .map(each -> TotalRow.of(each.recordedBy(), each.total()))
                .toList());
    }

    private static String patientLabel(Visit visit, Map<UUID, PatientSummary> patients) {
        PatientSummary patient = patients.get(visit.patientId());
        return patient == null ? "(paciente desconocido)"
                : patient.document() == null ? patient.fullName()
                : patient.fullName() + " — " + patient.document();
    }

    /** An amount as a row shows it: what went out of the drawer, with a minus. */
    private static String signed(Movement movement) {
        String pesos = CashDeskWords.pesos(movement.amount().value());
        return movement.kind() == Kind.REFUND ? "−" + pesos : pesos;
    }

    /**
     * A charge or a refund as reception's screen lists it. A charge that is its visit's current one may be
     * given back, up to what is left of it; the last charge of a visit given back whole may be charged again.
     *
     * @param left what is left of the charge, as the form takes it; null when nothing may be given back
     */
    record Row(UUID visitId, String kind, String time, String patient, boolean emergency, String means,
            String amount, String reason, String user, String left, String leftLabel, boolean chargeAgain) {

        static Row of(Movement movement, Map<UUID, PatientSummary> patients) {
            Visit visit = movement.visit();
            boolean charge = movement.kind() == Kind.CHARGE;
            boolean current = charge && visit.current().map(movement.charge()::equals).orElse(false);
            boolean last = charge && visit.charges().getLast().equals(movement.charge());
            return new Row(visit.id(), CashDeskWords.kind(movement.kind()), TIME.format(movement.recordedAt()),
                    patientLabel(visit, patients), visit.emergency(), CashDeskWords.means(movement.means()),
                    signed(movement), movement.reason(), movement.recordedBy(),
                    current ? movement.charge().left().toPlainString() : null,
                    current ? CashDeskWords.pesos(movement.charge().left()) : null,
                    last && visit.current().isEmpty());
        }
    }

    /** A charge or a refund as the admins' screen lists it: no patient, no reason. */
    record LogRow(String kind, String time, String means, String amount, String user) {

        static LogRow of(Movement movement) {
            return new LogRow(CashDeskWords.kind(movement.kind()), TIME.format(movement.recordedAt()),
                    CashDeskWords.means(movement.means()), signed(movement), movement.recordedBy());
        }
    }

    /** An emergency never charged. */
    record PendingRow(UUID visitId, String patient, String arrivedAt) {
    }

    /** What one user, or everyone, recorded: charged, given back, and what is left. */
    record TotalRow(String user, int charges, String charged, int refunds, String refunded, String left) {

        static TotalRow of(String user, Total total) {
            return new TotalRow(user, total.charged().count(), CashDeskWords.pesos(total.charged().amount()),
                    total.refunded().count(), CashDeskWords.pesos(total.refunded().amount()),
                    CashDeskWords.pesos(total.left()));
        }
    }
}
