package com.clinic.schedule.portal.adapter.in.screen;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.portal.application.exception.InvalidActivationLinkException;
import com.clinic.schedule.portal.application.usecase.ActivateAccountUseCase;
import com.clinic.schedule.portal.application.usecase.ListOwnAppointmentsUseCase;
import com.clinic.schedule.portal.domain.Appointment;
import com.clinic.schedule.portal.domain.OwnAppointments;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The patients' screens (D-80), in Spanish, since patients read them (D-13): logging in with their document,
 * activating their account from the email's link, and their appointments. Thin and throwaway, as every
 * screen (D-02): the patients' single-page application will replace them.
 */
@Controller
class PortalViewController {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));

    private final ActivateAccountUseCase activate;
    private final ListOwnAppointmentsUseCase appointments;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    PortalViewController(ActivateAccountUseCase activate, ListOwnAppointmentsUseCase appointments,
            PatientReader patients, PractitionerReader practitioners) {
        this.activate = activate;
        this.appointments = appointments;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    @GetMapping("/portal/login")
    String login(Model model) {
        model.addAttribute("documentTypes", patients.documentTypes());
        return "portal-login";
    }

    @GetMapping("/portal")
    String home() {
        return "redirect:/portal/appointments";
    }

    @GetMapping("/portal/activation")
    String activation(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("usable", activate.isUsable(token));
        return "portal-activation";
    }

    /** The password is asked twice, so a typo does not lock the patient out of an account just opened. */
    @PostMapping("/portal/activation")
    String activate(@RequestParam String token, @RequestParam String password, @RequestParam String repeatedPassword,
            Model model) {
        model.addAttribute("token", token);
        model.addAttribute("usable", true);
        if (!password.equals(repeatedPassword)) {
            model.addAttribute("error", "Las dos contraseñas no coinciden.");
            return "portal-activation";
        }
        try {
            activate.handle(token, password);
            return "redirect:/portal/login?activated";
        } catch (InvalidActivationLinkException e) {
            model.addAttribute("usable", false);
            return "portal-activation";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "La contraseña tiene de 10 a 72 caracteres.");
            return "portal-activation";
        }
    }

    @GetMapping("/portal/appointments")
    String appointments(Model model) {
        OwnAppointments own = appointments.handle();
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        model.addAttribute("upcoming", rows(own.upcoming(), names));
        model.addAttribute("past", rows(own.past(), names));
        return "portal-appointments";
    }

    private static List<Row> rows(List<Appointment> appointments, Map<UUID, String> names) {
        return appointments.stream().map(appointment -> Row.of(appointment, names)).toList();
    }

    /** One of the patient's appointments, in Spanish. Never the note nor who cancelled it. */
    record Row(String when, String practitioner, String reason, String state) {

        static Row of(Appointment appointment, Map<UUID, String> names) {
            return new Row(WHEN.format(appointment.startsAt()), names.getOrDefault(appointment.practitionerId(), ""),
                    appointment.reason(), state(appointment));
        }

        private static String state(Appointment appointment) {
            return switch (appointment.state()) {
                case BOOKED -> "Reservado";
                case ABSENT -> "No asististe";
                case CANCELLED -> appointment.cancellationRequestedBy() == null ? "Cancelado"
                        : switch (appointment.cancellationRequestedBy()) {
                            case PATIENT -> "Cancelado a tu pedido";
                            case PRACTICE -> "Cancelado por el consultorio";
                        };
            };
        }
    }
}
