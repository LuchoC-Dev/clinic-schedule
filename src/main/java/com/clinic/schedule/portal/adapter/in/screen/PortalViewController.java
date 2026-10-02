package com.clinic.schedule.portal.adapter.in.screen;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.portal.application.exception.InvalidActivationLinkException;
import com.clinic.schedule.portal.application.exception.OwnDataRefusedException;
import com.clinic.schedule.portal.application.usecase.ActivateAccountUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnConsentUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnContactUseCase;
import com.clinic.schedule.portal.application.usecase.ListOwnAppointmentsUseCase;
import com.clinic.schedule.portal.application.usecase.ViewOwnDataUseCase;
import com.clinic.schedule.portal.domain.Appointment;
import com.clinic.schedule.portal.domain.OwnAppointments;
import com.clinic.schedule.portal.domain.OwnContact;
import com.clinic.schedule.portal.domain.OwnData;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The patients' screens (D-80), in Spanish, since patients read them (D-13): logging in with their document,
 * activating their account from the email's link, their appointments, and their data, which they change
 * themselves (D-81). Thin and throwaway, as every screen (D-02): the patients' single-page application will
 * replace them.
 */
@Controller
class PortalViewController {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d/MM/yyyy");

    private final ActivateAccountUseCase activate;
    private final ListOwnAppointmentsUseCase appointments;
    private final ViewOwnDataUseCase data;
    private final ChangeOwnContactUseCase contact;
    private final ChangeOwnConsentUseCase consent;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    PortalViewController(ActivateAccountUseCase activate, ListOwnAppointmentsUseCase appointments,
            ViewOwnDataUseCase data, ChangeOwnContactUseCase contact, ChangeOwnConsentUseCase consent,
            PatientReader patients, PractitionerReader practitioners) {
        this.activate = activate;
        this.appointments = appointments;
        this.data = data;
        this.contact = contact;
        this.consent = consent;
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
