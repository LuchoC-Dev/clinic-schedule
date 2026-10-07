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
import com.clinic.schedule.cashdesk.application.usecase.ViewCashDeskDayUseCase;
import com.clinic.schedule.cashdesk.domain.CashDeskDay;
import com.clinic.schedule.cashdesk.domain.Charge;
import com.clinic.schedule.cashdesk.domain.Visit;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * The day's cash desk (D-87). Reception's screen, {@code /cash-desk}, shows it whole and charges the
 * pending emergencies; the admins', {@code /cash-desk-log}, shows the charges and the totals without the
 * patients and the pending visits. Which role reaches which is the route's to say (D-34).
 */
@Controller
class CashDeskViewController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ViewCashDeskDayUseCase view;
    private final ChargeVisitUseCase charge;
    private final PatientReader patients;

    CashDeskViewController(ViewCashDeskDayUseCase view, ChargeVisitUseCase charge, PatientReader patients) {
        this.view = view;
        this.charge = charge;
        this.patients = patients;
    }

    @GetMapping("/cash-desk")
    String forReception(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        CashDeskDay day = view.handle(date);
        Map<UUID, PatientSummary> patientsById = patients.findAll(Stream.concat(day.charged().stream(),
                        day.pending().stream()).map(Visit::patientId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        totals(day, model);
        model.addAttribute("rows", day.charged().stream().map(visit -> Row.of(visit, patientsById)).toList());
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
        model.addAttribute("rows", day.charged().stream().map(LogRow::of).toList());
        return "cash-desk-log";
    }

    /** An emergency's pending charge, recorded; back to the day it was on. */
    @PostMapping("/cash-desk/visits/{visitId}/charge")
    String charge(@PathVariable UUID visitId, @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String date, RedirectAttributes flash) {
        try {
            charge.handle(new ChargeVisitUseCase.Command(visitId, amount));
            flash.addFlashAttribute("message", "Cobro registrado.");
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
        model.addAttribute("count", day.total().count());
        model.addAttribute("total", CashDeskWords.pesos(day.total().amount()));
        model.addAttribute("byRecorder", day.byRecorder().stream()
                .map(each -> new RecorderRow(each.recordedBy(), each.total().count(), CashDeskWords.pesos(each.total().amount())))
                .toList());
    }

    private static String patientLabel(Visit visit, Map<UUID, PatientSummary> patients) {
        PatientSummary patient = patients.get(visit.patientId());
        return patient == null ? "(paciente desconocido)"
                : patient.document() == null ? patient.fullName()
                : patient.fullName() + " — " + patient.document();
    }

    /** A charge as reception's screen lists it. */
    record Row(String time, String patient, boolean emergency, String means, String amount, String user) {

        static Row of(Visit visit, Map<UUID, PatientSummary> patients) {
            Charge charge = visit.charge().orElseThrow();
            return new Row(TIME.format(charge.recordedAt()), patientLabel(visit, patients), visit.emergency(),
                    CashDeskWords.means(charge.means()), CashDeskWords.pesos(charge.amount().value()), charge.recordedBy());
        }
    }

    /** A charge as the admins' screen lists it: no patient. */
    record LogRow(String time, String means, String amount, String user) {

        static LogRow of(Visit visit) {
            Charge charge = visit.charge().orElseThrow();
            return new LogRow(TIME.format(charge.recordedAt()), CashDeskWords.means(charge.means()),
                    CashDeskWords.pesos(charge.amount().value()), charge.recordedBy());
        }
    }

    /** An emergency not charged yet. */
    record PendingRow(UUID visitId, String patient, String arrivedAt) {
    }

    /** What one user recorded. */
    record RecorderRow(String user, int count, String amount) {
    }
}
