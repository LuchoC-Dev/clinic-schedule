package com.clinic.schedule.reminder.adapter.in.screen;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;
import com.clinic.schedule.reminder.application.usecase.ListRemindersUseCase;
import com.clinic.schedule.reminder.domain.Reminder;
import com.clinic.schedule.reminder.domain.ReminderOutcome;

/**
 * Reception's screen of the reminders, by the appointment's day (D-78): for each appointment, whether its
 * reminder went out, and if not, why — so reception calls the patients it did not reach.
 */
@Controller
class ReminderViewController {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE d/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));

    private final ListRemindersUseCase reminders;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    ReminderViewController(ListRemindersUseCase reminders, PatientReader patients, PractitionerReader practitioners) {
        this.reminders = reminders;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    @GetMapping("/reminders")
    String reminders(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        ListRemindersUseCase.Result result;
        try {
            result = reminders.handle(from, to);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "El primer día es posterior al último.");
            model.addAttribute("from", from);
            model.addAttribute("to", to);
            model.addAttribute("rows", List.of());
            return "reminders";
        }
        Map<UUID, PatientSummary> patientsById = patients.findAll(result.reminders().stream()
                        .map(Reminder::patientId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        model.addAttribute("from", result.from());
        model.addAttribute("to", result.to());
        model.addAttribute("rows", result.reminders().stream()
                .map(reminder -> Row.of(reminder, patientsById, names))
                .toList());
        return "reminders";
    }

    private static String when(LocalDateTime at) {
        return WHEN.format(at);
    }

    /** A reminder as the screen lists it, in Spanish (D-13). {@code failed} marks the ones to call about. */
    record Row(String appointment, String practitioner, UUID patientId, String patient, String recordedAt,
            String outcome, boolean failed) {

        static Row of(Reminder reminder, Map<UUID, PatientSummary> patients, Map<UUID, String> names) {
            PatientSummary patient = patients.get(reminder.patientId());
            String practitioner = names.get(reminder.practitionerId());
            return new Row(when(reminder.startsAt()),
                    practitioner == null ? "(profesional desconocido)" : practitioner,
                    reminder.patientId(),
                    patient == null ? "(paciente desconocido)"
                            : patient.document() == null ? patient.fullName()
                            : patient.fullName() + " — " + patient.document(),
                    when(reminder.recordedAt()), outcome(reminder),
                    reminder.outcome() == ReminderOutcome.FAILED);
        }

        private static String outcome(Reminder reminder) {
            return switch (reminder.outcome()) {
                case SENT -> "Enviado por email";
                case FAILED -> "No se pudo enviar: avisale por teléfono";
                case NOT_SENT -> switch (reminder.reason()) {
                    case NO_EMAIL -> "No enviado: no tiene email";
                    case NO_CONSENT -> "No enviado: sin consentimiento para el email";
                    case CONFIDENTIAL -> "No enviado: el email es de un responsable que dio el consentimiento, y el "
                            + "paciente tiene 13 años o más o no tiene fecha de nacimiento";
                };
            };
        }
    }
}
