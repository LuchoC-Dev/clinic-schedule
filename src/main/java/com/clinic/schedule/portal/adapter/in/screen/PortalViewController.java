package com.clinic.schedule.portal.adapter.in.screen;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.portal.application.exception.InvalidActivationLinkException;
import com.clinic.schedule.portal.application.exception.OwnDataRefusedException;
import com.clinic.schedule.portal.application.usecase.ActivateAccountUseCase;
import com.clinic.schedule.portal.application.usecase.BookOnWebUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnConsentUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnContactUseCase;
import com.clinic.schedule.portal.application.usecase.ListOwnAppointmentsUseCase;
import com.clinic.schedule.portal.application.usecase.ViewOwnDataUseCase;
import com.clinic.schedule.portal.application.usecase.ViewWebBookingUseCase;
import com.clinic.schedule.portal.domain.Appointment;
import com.clinic.schedule.portal.domain.BookableReason;
import com.clinic.schedule.portal.domain.OwnAppointments;
import com.clinic.schedule.portal.domain.OwnContact;
import com.clinic.schedule.portal.domain.OwnData;
import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The patients' screens (D-80), in Spanish, since patients read them (D-13): logging in with their document,
 * activating their account from the email's link, their appointments, their data, which they change
 * themselves (D-81), and booking an appointment (D-82). Thin and throwaway, as every screen (D-02): the
 * patients' single-page application will replace them.
 */
@Controller
class PortalViewController {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d/MM/yyyy");
    private static final DateTimeFormatter DAY_NAME =
            DateTimeFormatter.ofPattern("EEEE d/MM/yyyy", Locale.forLanguageTag("es-AR"));

    private final ActivateAccountUseCase activate;
    private final ListOwnAppointmentsUseCase appointments;
    private final ViewOwnDataUseCase data;
    private final ChangeOwnContactUseCase contact;
    private final ChangeOwnConsentUseCase consent;
    private final ViewWebBookingUseCase booking;
    private final BookOnWebUseCase book;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    PortalViewController(ActivateAccountUseCase activate, ListOwnAppointmentsUseCase appointments,
            ViewOwnDataUseCase data, ChangeOwnContactUseCase contact, ChangeOwnConsentUseCase consent,
            ViewWebBookingUseCase booking, BookOnWebUseCase book, PatientReader patients,
            PractitionerReader practitioners) {
        this.activate = activate;
        this.appointments = appointments;
        this.data = data;
        this.contact = contact;
        this.consent = consent;
        this.booking = booking;
        this.book = book;
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

    /**
     * Booking, in steps that come back to the same screen: a reason booked on the web, with its practitioner,
     * and a day, then the times the agenda offers that day, each a button that books it.
     */
    @GetMapping("/portal/booking")
    String booking(@RequestParam(required = false) UUID reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        ViewWebBookingUseCase.Options options = booking.options();
        model.addAttribute("reasons", options.reasons());
        model.addAttribute("firstDay", options.firstDay());
        model.addAttribute("lastDay", options.lastDay());
        model.addAttribute("lastDayLabel", DAY.format(options.lastDay()));
        model.addAttribute("reason", reason);
        model.addAttribute("date", date == null ? options.firstDay() : date);
        BookableReason chosen = options.reasons().stream()
                .filter(candidate -> candidate.reasonId().equals(reason))
                .findFirst()
                .orElse(null);
        if (chosen != null && date != null) {
            try {
                model.addAttribute("chosen", chosen);
                model.addAttribute("day", DAY_NAME.format(date));
                model.addAttribute("times", booking.times(chosen.practitionerId(), chosen.reasonId(), date));
            } catch (WebBookingRefusedException e) {
                model.addAttribute("error", refusal(e));
            }
        }
        return "portal-booking";
    }

    @PostMapping("/portal/booking")
    String book(@RequestParam UUID reason, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startsAt, RedirectAttributes redirect) {
        BookableReason chosen = booking.options().reasons().stream()
                .filter(candidate -> candidate.reasonId().equals(reason))
                .findFirst()
                .orElse(null);
        try {
            if (chosen == null) {
                throw new WebBookingRefusedException(WebBookingRefusedException.Reason.NOT_ON_THE_WEB,
                        "That reason is not booked on the web");
            }
            BookOnWebUseCase.Result booked = book.handle(chosen.practitionerId(), chosen.reasonId(), startsAt);
            redirect.addFlashAttribute("message", "Turno reservado: " + WHEN.format(booked.startsAt()) + " con "
                    + booked.practitioner() + (booked.confirmationSent()
                            ? ". Te mandamos la confirmación por email."
                            : ". No pudimos mandarte la confirmación por email, pero el turno quedó reservado."));
            return "redirect:/portal/appointments";
        } catch (WebBookingRefusedException e) {
            redirect.addFlashAttribute("error", refusal(e));
            return "redirect:/portal/booking?reason=" + reason + "&date=" + startsAt.toLocalDate();
        }
    }

    /** What the patient reads when the web does not book what they asked for. */
    private static String refusal(WebBookingRefusedException e) {
        return switch (e.reason()) {
            case NOT_ON_THE_WEB -> "Ese motivo no se reserva por la web: llamá o acercate a recepción.";
            case DAY_OUT_OF_RANGE ->
                    "Ese día no se reserva por la web: elegí uno entre mañana y el último que ofrece la pantalla.";
            case NOT_AN_OFFERED_TIME, TIME_TAKEN -> "Ese horario ya no está libre: elegí otro.";
            case TOO_MANY_AHEAD -> "Ya tenés 3 turnos por delante: para sacar otro, llamá o acercate a recepción.";
            case PATIENT_BUSY -> "Ya tenés otro turno a esa hora.";
        };
    }

    @GetMapping("/portal/profile")
    String profile(Model model) {
        OwnData own = data.handle();
        model.addAttribute("data", own);
        model.addAttribute("birthDate", own.birthDate() == null ? null : DAY.format(own.birthDate()));
        model.addAttribute("relations", relations());
        model.addAttribute("phoneConsent", consentChoices(own.phone()));
        model.addAttribute("emailConsent", consentChoices(own.email()));
        return "portal-profile";
    }

    @PostMapping("/portal/profile/details")
    String changeDetails(@RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String phoneOwnerName,
            @RequestParam(required = false) String phoneOwnerRelation,
            @RequestParam(required = false) String emailAddress,
            @RequestParam(required = false) String emailOwnerName,
            @RequestParam(required = false) String emailOwnerRelation,
            @RequestParam(required = false) String insurer, @RequestParam(required = false) String memberNumber,
            @RequestParam(required = false) String plan, RedirectAttributes redirect) {
        try {
            contact.handle(new OwnContact(phoneNumber, phoneOwnerName, phoneOwnerRelation, emailAddress,
                    emailOwnerName, emailOwnerRelation, insurer, memberNumber, plan));
            redirect.addFlashAttribute("message", "Tus datos quedaron guardados.");
        } catch (OwnDataRefusedException e) {
            redirect.addFlashAttribute("error", "Revisá tus datos: el teléfono lleva de 8 a 15 dígitos; el email, "
                    + "la forma nombre@dominio.com; si el teléfono o el email son de otra persona, van con su nombre "
                    + "y su vínculo; y el número de afiliado y el plan van con el nombre de la obra social.");
        }
        return "redirect:/portal/profile";
    }

    /** An empty choice leaves the channel as it is: it is offered only while its owner's consent stands. */
    @PostMapping("/portal/profile/contact-consent")
    String changeConsent(@RequestParam(required = false) Boolean phoneConsent,
            @RequestParam(required = false) Boolean emailConsent, RedirectAttributes redirect) {
        try {
            consent.handle(phoneConsent, emailConsent);
            redirect.addFlashAttribute("message", "Tus recordatorios quedaron guardados.");
        } catch (OwnDataRefusedException e) {
            redirect.addFlashAttribute("error",
                    "Para recibir recordatorios en un teléfono o un email, primero cargalo en tus datos.");
        }
        return "redirect:/portal/profile";
    }

    /** The relations an owner may have to the patient, in Spanish. */
    private static Map<String, String> relations() {
        Map<String, String> relations = new LinkedHashMap<>();
        relations.put("MOTHER", "Madre");
        relations.put("FATHER", "Padre");
        relations.put("GUARDIAN", "Tutor o tutora");
        relations.put("PARTNER", "Pareja");
        relations.put("CHILD", "Hijo o hija");
        relations.put("OTHER", "Otra persona");
        return relations;
    }

    /**
     * The choices for a channel's reminders, the one that stands first: "" keeps the consent its owner gave,
     * true is the patient's own, false is none. None at all for a channel that is not there.
     */
    private static Map<String, String> consentChoices(OwnData.Channel channel) {
        Map<String, String> choices = new LinkedHashMap<>();
        if (channel == null) {
            return choices;
        }
        if (channel.consent() == OwnData.Consent.OWNER) {
            choices.put("", "Sí: lo autorizó " + channel.ownerName());
        }
        if (channel.consent() == null) {
            choices.put("false", "No");
        }
        choices.put("true", "Sí, lo autorizo yo");
        choices.put("false", "No");
        return choices;
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
