package com.clinic.schedule.appointment.adapter.in.screen;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.appointment.application.exception.TimeOffInThePastException;
import com.clinic.schedule.appointment.application.exception.UnknownPractitionerException;
import com.clinic.schedule.appointment.application.usecase.RecordTimeOffUseCase;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * Records a practitioner's absence from the practitioner screen, and goes back to it saying
 * what the absence did to the agenda (D-46).
 *
 * <p>It lives in this module and not in {@code practitioner}, as the API does (D-32): what
 * matters is the cascade over the appointments, which only this module knows. The practitioner
 * screen only holds the form. Throwaway like every screen (D-02).
 */
@Controller
class TimeOffViewController {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d/MM HH:mm", Locale.of("es", "AR"));

    private final RecordTimeOffUseCase recordTimeOff;
    private final PatientReader patients;

    TimeOffViewController(RecordTimeOffUseCase recordTimeOff, PatientReader patients) {
        this.recordTimeOff = recordTimeOff;
        this.patients = patients;
    }

    /**
     * Without times the absence is whole days, from the first day to the last; with both times,
     * part of a single day.
     */
    @PostMapping("/time-off")
    String record(@RequestParam UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate untilDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startsAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endsAt,
            RedirectAttributes flash) {
        String back = "redirect:/practitioners/" + practitionerId;
        RecordTimeOffUseCase.Result result;
        try {
            result = recordTimeOff.handle(RecordTimeOffUseCase.Command.of(
                    new PractitionerId(practitionerId), date, untilDate, startsAt, endsAt));
        } catch (TimeOffInThePastException e) {
            flash.addFlashAttribute("error", "No se registran ausencias en días que ya pasaron.");
            return back;
        } catch (UnknownPractitionerException e) {
            flash.addFlashAttribute("error", "No se encontró el profesional.");
            return "redirect:/practitioners";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", "Revisá la ausencia: sin horario son días completos; con horario, "
                    + "es un solo día y termina después de empezar.");
            return back;
        }

        flash.addFlashAttribute("message", result.cancelled().isEmpty()
                ? "Ausencia registrada. No tocaba ningún turno."
                : "Ausencia registrada. Turnos cancelados (" + result.cancelled().size() + "): "
                        + listed(result.cancelled()) + ".");
        if (!result.needsManualHandling().isEmpty()) {
            flash.addFlashAttribute("error", "Estos pacientes ya llegaron y sus turnos siguen en pie: resolvelos a mano. "
                    + listed(result.needsManualHandling()) + ".");
        }
        return back;
    }

    /** "jue 24/09 10:00 Ana Pérez, jue 24/09 11:00 Juan Gómez". */
    private String listed(List<Appointment> appointments) {
        Map<UUID, String> names = patients.findAll(
                        appointments.stream().map(appointment -> appointment.patientId().value()).toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, PatientSummary::fullName));
        return appointments.stream()
                .map(appointment -> WHEN.format(appointment.slot().startsAt()) + " "
                        + names.getOrDefault(appointment.patientId().value(), "(paciente desconocido)"))
                .collect(Collectors.joining(", "));
    }
}
