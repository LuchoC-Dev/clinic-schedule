package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.clinic.schedule.clinicalrecord.application.usecase.ListMyOrdersUseCase;
import com.clinic.schedule.clinicalrecord.domain.CheckResult;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The practitioner's follow-up of the orders they issued (D-56): those still waiting — open, or
 * done and waiting for the result — the oldest first; and those reported, the newest first, from
 * the last {@link #RECENT_DAYS} days unless all are asked for. Nothing marks a result as seen, so
 * how recent it is stands for how new it is.
 */
@Controller
class MyOrdersViewController {

    /** How far back the reported orders go unless all are asked for. */
    static final int RECENT_DAYS = 30;

    private final ListMyOrdersUseCase myOrders;
    private final PatientReader patients;
    private final PractitionerReader practitioners;
    private final Clock clock;

    MyOrdersViewController(ListMyOrdersUseCase myOrders, PatientReader patients, PractitionerReader practitioners,
            Clock clock) {
        this.myOrders = myOrders;
        this.patients = patients;
        this.practitioners = practitioners;
        this.clock = clock;
    }

    @GetMapping("/orders/mine")
    String mine(@RequestParam(defaultValue = "false") boolean all, Model model) {
        List<ListMyOrdersUseCase.MyOrder> found = myOrders.handle();
        Map<UUID, PatientSummary> patientsById = patients.findAll(found.stream()
                        .map(mine -> mine.order().patientId().value())
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        LocalDateTime since = LocalDate.now(clock).minusDays(RECENT_DAYS).atStartOfDay();

        model.addAttribute("waiting", found.stream()
                .filter(mine -> mine.situation() != ListMyOrdersUseCase.Situation.REPORTED)
                .sorted(Comparator.comparing(mine -> mine.order().orderedAt()))
                .map(mine -> MyOrderRow.of(mine, patientsById, names))
                .toList());
        model.addAttribute("reported", found.stream()
                .filter(mine -> mine.situation() == ListMyOrdersUseCase.Situation.REPORTED)
                .filter(mine -> all || !reportedAt(mine).isBefore(since))
                .sorted(Comparator.comparing(MyOrdersViewController::reportedAt).reversed())
                .map(mine -> MyOrderRow.of(mine, patientsById, names))
                .toList());
        model.addAttribute("all", all);
        model.addAttribute("recentDays", RECENT_DAYS);
        // The header shows a practitioner's links (D-34).
        model.addAttribute("readOnly", true);
        return "my-orders";
    }

    private static LocalDateTime reportedAt(ListMyOrdersUseCase.MyOrder mine) {
        return mine.check().flatMap(check -> check.result()).map(CheckResult::writtenAt).orElseThrow();
    }

    /** One order as the follow-up list shows it: no result text, only where it stands. */
    record MyOrderRow(UUID patientId, String patient, String description, String orderedAt, String state,
            boolean mayRead) {

        static MyOrderRow of(ListMyOrdersUseCase.MyOrder mine, Map<UUID, PatientSummary> patients,
                Map<UUID, String> names) {
            PatientSummary patient = patients.get(mine.order().patientId().value());
            String patientLabel = patient == null ? "(paciente desconocido)"
                    : patient.document() == null ? patient.fullName()
                    : patient.fullName() + " — " + patient.document();
            String state = switch (mine.situation()) {
                case OPEN -> "Abierta: nadie hizo el chequeo todavía";
                case AWAITING_RESULT -> mine.check()
                        .map(check -> "Realizada el " + ClinicalRecordEntry.format(check.performedAt()) + " por "
                                + ClinicalRecordEntry.nameOf(names, check.performedBy().value())
                                + "; espera el resultado")
                        .orElseThrow();
                case REPORTED -> mine.check()
                        .flatMap(check -> check.result())
                        .map(result -> "Resultado del " + ClinicalRecordEntry.format(result.writtenAt()) + ", de "
                                + ClinicalRecordEntry.nameOf(names, result.author().value()))
                        .orElseThrow();
            };
            return new MyOrderRow(mine.order().patientId().value(), patientLabel, mine.order().description(),
                    ClinicalRecordEntry.format(mine.order().orderedAt()), state, mine.mayRead());
        }
    }
}
