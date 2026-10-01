package com.clinic.schedule.appointment.adapter.in.screen;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.clinic.schedule.appointment.application.usecase.ListCancellationsUseCase;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.Cancellation;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The cancellations, searched by the days they were made and by practitioner (D-76). Reception's
 * screen, {@code /cancellations}, shows them whole; the admins', {@code /cancellation-log}, without the
 * patient and the note. Which role reaches which is the route's to say (D-34).
 */
@Controller
class CancellationViewController {

    private final ListCancellationsUseCase cancellations;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    CancellationViewController(ListCancellationsUseCase cancellations, PatientReader patients,
            PractitionerReader practitioners) {
        this.cancellations = cancellations;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    @GetMapping("/cancellations")
    String forReception(@RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        ListCancellationsUseCase.Result result = search(practitionerId, from, to, model);
        if (result == null) {
            return "cancellations";
        }
        Map<UUID, PatientSummary> patientsById = patients.findAll(result.cancelled().stream()
                        .map(appointment -> appointment.patientId().value())
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<UUID, String> names = practitionerNames();
        model.addAttribute("rows", result.cancelled().stream()
                .map(appointment -> Row.of(appointment, names, patientsById))
                .toList());
        return "cancellations";
    }

    @GetMapping("/cancellation-log")
    String forAdmins(@RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        // The admins' header, which offers their screens alone (D-68).
        model.addAttribute("admin", true);
        ListCancellationsUseCase.Result result = search(practitionerId, from, to, model);
        if (result == null) {
            return "cancellation-log";
        }
        Map<UUID, String> names = practitionerNames();
        model.addAttribute("rows", result.cancelled().stream()
                .map(appointment -> LogRow.of(appointment, names))
                .toList());
        return "cancellation-log";
    }

    /** What both screens search and show alike; null, with the error on screen, when the days are reversed. */
    private ListCancellationsUseCase.Result search(UUID practitionerId, LocalDate from, LocalDate to, Model model) {
        model.addAttribute("practitionerId", practitionerId);
        model.addAttribute("practitioners", practitioners.findAll());
        ListCancellationsUseCase.Result result;
        try {
            result = cancellations.handle(new ListCancellationsUseCase.Command(
                    practitionerId == null ? null : new PractitionerId(practitionerId), from, to));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "El primer día es posterior al último.");
            model.addAttribute("from", from);
            model.addAttribute("to", to);
            model.addAttribute("rows", List.of());
            return null;
        }
        model.addAttribute("from", result.from());
        model.addAttribute("to", result.to());
        model.addAttribute("limitReached", result.limitReached());
        return result;
    }

    private Map<UUID, String> practitionerNames() {
        return practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
    }

    private static String practitionerName(Appointment appointment, Map<UUID, String> names) {
        String name = names.get(appointment.practitionerId().value());
        return name == null ? "(profesional desconocido)" : name;
    }

    /** A cancellation as reception's screen lists it. */
    record Row(String when, String user, String origin, String requestedBy, String appointment, String practitioner,
            UUID patientId, String patient, String note) {

        static Row of(Appointment appointment, Map<UUID, String> names, Map<UUID, PatientSummary> patients) {
            Cancellation cancellation = appointment.cancellation().orElseThrow();
            PatientSummary patient = patients.get(appointment.patientId().value());
            String patientLabel = patient == null ? "(paciente desconocido)"
                    : patient.document() == null ? patient.fullName()
                    : patient.fullName() + " — " + patient.document();
            return new Row(CancellationWords.when(cancellation.at()), cancellation.by(),
                    CancellationWords.origin(cancellation), CancellationWords.requester(cancellation.requestedBy()),
                    CancellationWords.when(appointment.slot().startsAt()), practitionerName(appointment, names),
                    appointment.patientId().value(), patientLabel, cancellation.note());
        }
    }

    /** A cancellation as the admins' screen lists it: no patient, no note. */
    record LogRow(String when, String user, String origin, String requestedBy, String appointment,
            String practitioner) {

        static LogRow of(Appointment appointment, Map<UUID, String> names) {
            Cancellation cancellation = appointment.cancellation().orElseThrow();
            return new LogRow(CancellationWords.when(cancellation.at()), cancellation.by(),
                    CancellationWords.origin(cancellation), CancellationWords.requester(cancellation.requestedBy()),
                    CancellationWords.when(appointment.slot().startsAt()), practitionerName(appointment, names));
        }
    }
}
