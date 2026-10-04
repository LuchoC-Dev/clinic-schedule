package com.clinic.schedule.portal.adapter.in.screen;

import com.clinic.schedule.portal.application.usecase.RecoverPasswordUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnPasswordUseCase;
import com.clinic.schedule.portal.application.exception.OwnPasswordRefusedException;
import com.clinic.schedule.portal.application.exception.InvalidResetLinkException;
import com.clinic.schedule.portal.domain.exception.FamilyRefusedException;
import com.clinic.schedule.portal.domain.AgeBand;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.application.usecase.ViewFamilyUseCase;
import com.clinic.schedule.portal.application.usecase.ManageOwnFamilyUseCase;
import com.clinic.schedule.portal.application.exception.NotYourFamilyMemberException;
import com.clinic.schedule.portal.application.exception.FamilyGroupNotFoundException;
import java.time.Clock;
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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.portal.application.exception.InvalidActivationLinkException;
import com.clinic.schedule.portal.application.exception.NotYourAppointmentException;
import com.clinic.schedule.portal.application.exception.OwnDataRefusedException;
import com.clinic.schedule.portal.application.usecase.ActivateAccountUseCase;
import com.clinic.schedule.portal.application.usecase.BookOnWebUseCase;
import com.clinic.schedule.portal.application.usecase.CancelLinkUseCase;
import com.clinic.schedule.portal.application.usecase.CancelOwnAppointmentUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnConsentUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnContactUseCase;
import com.clinic.schedule.portal.application.usecase.ListOwnAppointmentsUseCase;
import com.clinic.schedule.portal.application.usecase.ViewOwnDataUseCase;
import com.clinic.schedule.portal.application.usecase.ViewWebBookingUseCase;
import com.clinic.schedule.portal.domain.Appointment;
import com.clinic.schedule.portal.domain.BookableReason;
import com.clinic.schedule.portal.domain.BookedAppointment;
import com.clinic.schedule.portal.domain.OwnAppointments;
import com.clinic.schedule.portal.domain.OwnContact;
import com.clinic.schedule.portal.domain.OwnData;
import com.clinic.schedule.portal.domain.WebCancellationPolicy;
import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException;
import com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The patients' screens (D-80), in Spanish, since patients read them (D-13): logging in with their document,
 * activating their account from the email's link, their appointments, their data, which they change
 * themselves (D-81), booking an appointment (D-82) and cancelling one, from their account or from an email's
 * link with nobody logged in (D-83), and their family: the appointments of the members they act for, booked and
 * cancelled as their own, and the groups they are in (D-85); and their password, changed or recovered by
 * email (D-86). Thin and throwaway, as every screen (D-02): the
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
    private final CancelOwnAppointmentUseCase cancel;
    private final CancelLinkUseCase cancelLinks;
    private final ViewFamilyUseCase family;
    private final ManageOwnFamilyUseCase ownFamily;
    private final RecoverPasswordUseCase recovery;
    private final ChangeOwnPasswordUseCase ownPassword;
    private final PatientReader patients;
    private final PractitionerReader practitioners;
    private final Clock clock;
    private final int cancelHoursBefore;

    PortalViewController(ActivateAccountUseCase activate, ListOwnAppointmentsUseCase appointments,
            ViewOwnDataUseCase data, ChangeOwnContactUseCase contact, ChangeOwnConsentUseCase consent,
            ViewWebBookingUseCase booking, BookOnWebUseCase book, CancelOwnAppointmentUseCase cancel,
            CancelLinkUseCase cancelLinks, ViewFamilyUseCase family, ManageOwnFamilyUseCase ownFamily,
            RecoverPasswordUseCase recovery, ChangeOwnPasswordUseCase ownPassword, PatientReader patients,
            PractitionerReader practitioners, Clock clock,
            @Value("${app.portal.cancel-hours-before}") int cancelHoursBefore) {
        this.activate = activate;
        this.appointments = appointments;
        this.data = data;
        this.contact = contact;
        this.consent = consent;
        this.booking = booking;
        this.book = book;
        this.cancel = cancel;
        this.cancelLinks = cancelLinks;
        this.family = family;
        this.ownFamily = ownFamily;
        this.recovery = recovery;
        this.ownPassword = ownPassword;
        this.patients = patients;
        this.practitioners = practitioners;
        this.clock = clock;
        this.cancelHoursBefore = cancelHoursBefore;
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

    /** Asking for the link to choose a new password, with the document (D-86). */
    @GetMapping("/portal/password-recovery")
    String passwordRecovery(Model model) {
        model.addAttribute("documentTypes", patients.documentTypes());
        return "portal-password-recovery";
    }

    /** The same answer whether the document has an account or not. */
    @PostMapping("/portal/password-recovery")
    String recoverPassword(@RequestParam String documentType, @RequestParam String documentNumber,
            RedirectAttributes redirect) {
        recovery.ask(documentType, documentNumber);
        redirect.addFlashAttribute("message", "Si ese documento tiene una cuenta, le mandamos un link al email de "
                + "la cuenta para elegir una contraseña nueva. Vale una hora.");
        return "redirect:/portal/password-recovery";
    }

    @GetMapping("/portal/password-reset")
    String passwordReset(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("usable", recovery.isUsable(token));
        return "portal-password-reset";
    }

    /** The password is asked twice, as on activating the account. */
    @PostMapping("/portal/password-reset")
    String resetPassword(@RequestParam String token, @RequestParam String password,
            @RequestParam String repeatedPassword, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("usable", true);
        if (!password.equals(repeatedPassword)) {
            model.addAttribute("error", "Las dos contraseñas no coinciden.");
            return "portal-password-reset";
        }
        try {
            recovery.reset(token, password);
            return "redirect:/portal/login?reset";
        } catch (InvalidResetLinkException e) {
            model.addAttribute("usable", false);
            return "portal-password-reset";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "La contraseña tiene de 10 a 72 caracteres.");
            return "portal-password-reset";
        }
    }

    @GetMapping("/portal/password")
    String password() {
        return "portal-password";
    }

    @PostMapping("/portal/password")
    String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
            @RequestParam String repeatedPassword, RedirectAttributes redirect) {
        if (!newPassword.equals(repeatedPassword)) {
            redirect.addFlashAttribute("error", "Las dos contraseñas nuevas no coinciden.");
            return "redirect:/portal/password";
        }
        try {
            ownPassword.handle(currentPassword, newPassword);
            redirect.addFlashAttribute("message", "Tu contraseña quedó cambiada.");
        } catch (OwnPasswordRefusedException e) {
            redirect.addFlashAttribute("error", switch (e.reason()) {
                case WRONG_CURRENT -> "La contraseña actual no es esa.";
                case SAME -> "La contraseña nueva es igual a la actual: elegí otra.";
            });
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "La contraseña tiene de 10 a 72 caracteres.");
        }
        return "redirect:/portal/password";
    }

    @GetMapping("/portal/appointments")
    String appointments(Model model) {
        OwnAppointments own = appointments.handle();
        Map<UUID, String> names = practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        LocalDateTime now = LocalDateTime.now(clock);
        model.addAttribute("upcoming", upcoming(own, names, now, true));
        model.addAttribute("past", own.past().stream().map(appointment -> Row.of(appointment, names, false, true))
                .toList());
        model.addAttribute("family", appointments.family().stream()
                .map(member -> new MemberRows(member.member().fullName(),
                        upcoming(member.appointments(), names, now, false),
                        member.appointments().past().stream()
                                .map(appointment -> Row.of(appointment, names, false, false))
                                .toList()))
                .toList());
        return "portal-appointments";
    }

    private List<Row> upcoming(OwnAppointments appointments, Map<UUID, String> names, LocalDateTime now,
            boolean own) {
        return appointments.upcoming().stream()
                .map(appointment -> Row.of(appointment, names,
                        WebCancellationPolicy.canCancel(appointment, now, cancelHoursBefore), own))
                .toList();
    }

    @PostMapping("/portal/appointments/{id}/cancel")
    String cancel(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            cancel.handle(id);
            redirect.addFlashAttribute("message", "Turno cancelado.");
        } catch (NotYourAppointmentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (WebCancellationRefusedException e) {
            redirect.addFlashAttribute("error", cancellationRefusal(e));
        }
        return "redirect:/portal/appointments";
    }

    /** The page of an email's link, with nobody logged in: when and with whom, and a button (D-83). */
    @GetMapping("/portal/cancel")
    String cancelLink(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        try {
            BookedAppointment appointment = cancelLinks.read(token);
            model.addAttribute("when", WHEN.format(appointment.startsAt()));
            model.addAttribute("practitioner", practitionerName(appointment));
        } catch (WebCancellationRefusedException e) {
            model.addAttribute("error", cancellationRefusal(e));
        }
        return "portal-cancel";
    }

    @PostMapping("/portal/cancel")
    String cancelByLink(@RequestParam String token, RedirectAttributes redirect) {
        try {
            cancelLinks.cancel(token);
            return "redirect:/portal/cancel/done";
        } catch (WebCancellationRefusedException e) {
            redirect.addFlashAttribute("error", cancellationRefusal(e));
            return "redirect:/portal/cancel/done";
        }
    }

    @GetMapping("/portal/cancel/done")
    String cancelled() {
        return "portal-cancelled";
    }

    private String practitionerName(BookedAppointment appointment) {
        return practitioners.findAll().stream()
                .filter(practitioner -> practitioner.id().equals(appointment.practitionerId()))
                .map(PractitionerSummary::fullName)
                .findFirst()
                .orElse("");
    }

    /** What the patient reads when an appointment cannot be cancelled on the web. */
    private static String cancellationRefusal(WebCancellationRefusedException e) {
        return switch (e.reason()) {
            case NOT_BOOKED -> "Ese turno ya no está reservado.";
            case TOO_LATE -> "Falta menos de un día para el turno: para cancelarlo, llamá o acercate a recepción.";
            case GONE -> "Este link ya no sirve: el turno se movió o ya se canceló. Si querés cancelarlo, llamá o "
                    + "acercate a recepción.";
        };
    }

    /**
     * Booking, in steps that come back to the same screen: a reason booked on the web, with its practitioner,
     * and a day, then the times the agenda offers that day, each a button that books it.
     */
    @GetMapping("/portal/booking")
    String booking(@RequestParam(required = false) UUID reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID patient, Model model) {
        ViewWebBookingUseCase.Options options = booking.options();
        List<AccountHolder> whom = family.bookableFor();
        model.addAttribute("whom", whom);
        model.addAttribute("patient", patient == null ? whom.get(0).patientId() : patient);
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
            LocalDateTime startsAt, @RequestParam(required = false) UUID patient, RedirectAttributes redirect) {
        BookableReason chosen = booking.options().reasons().stream()
                .filter(candidate -> candidate.reasonId().equals(reason))
                .findFirst()
                .orElse(null);
        try {
            if (chosen == null) {
                throw new WebBookingRefusedException(WebBookingRefusedException.Reason.NOT_ON_THE_WEB,
                        "That reason is not booked on the web");
            }
            BookOnWebUseCase.Result booked = book.handle(patient, chosen.practitionerId(), chosen.reasonId(),
                    startsAt);
            redirect.addFlashAttribute("message", "Turno reservado"
                    + (booked.member() == null ? "" : " para " + booked.member()) + ": "
                    + WHEN.format(booked.startsAt()) + " con "
                    + booked.practitioner() + (booked.confirmationSent()
                            ? ". Te mandamos la confirmación por email."
                            : ". No pudimos mandarte la confirmación por email, pero el turno quedó reservado."));
            return "redirect:/portal/appointments";
        } catch (NotYourFamilyMemberException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (WebBookingRefusedException e) {
            redirect.addFlashAttribute("error", refusal(e, memberName(patient)));
            return "redirect:/portal/booking?reason=" + reason + "&date=" + startsAt.toLocalDate()
                    + (patient == null ? "" : "&patient=" + patient);
        }
    }

    /** The name of a member the patient acts for; null for the patient themselves. */
    private String memberName(UUID patient) {
        return family.actedFor().stream()
                .filter(member -> member.patientId().equals(patient))
                .map(AccountHolder::fullName)
                .findFirst()
                .orElse(null);
    }

    /**
     * What the patient reads when the web does not book what they asked for, when it was for themselves.
     */
    private static String refusal(WebBookingRefusedException e) {
        return refusal(e, null);
    }

    /** ... and when it was for a member of their family, named. */
    private static String refusal(WebBookingRefusedException e, String member) {
        if (member != null && e.reason() == WebBookingRefusedException.Reason.TOO_MANY_AHEAD) {
            return member + " ya tiene 3 turnos por delante: para sacar otro, llamá o acercate a recepción.";
        }
        if (member != null && e.reason() == WebBookingRefusedException.Reason.PATIENT_BUSY) {
            return member + " ya tiene otro turno a esa hora.";
        }
        return switch (e.reason()) {
            case NOT_ON_THE_WEB -> "Ese motivo no se reserva por la web: llamá o acercate a recepción.";
            case DAY_OUT_OF_RANGE ->
                    "Ese día no se reserva por la web: elegí uno entre mañana y el último que ofrece la pantalla.";
            case NOT_AN_OFFERED_TIME, TIME_TAKEN -> "Ese horario ya no está libre: elegí otro.";
            case TOO_MANY_AHEAD -> "Ya tenés 3 turnos por delante: para sacar otro, llamá o acercate a recepción.";
            case PATIENT_BUSY -> "Ya tenés otro turno a esa hora.";
        };
    }

    /** The patient's groups: who is in each, who shares, and the patient's own choices (D-85). */
    @GetMapping("/portal/family")
    String family(Model model) {
        model.addAttribute("groups", family.groups().stream().map(FamilyRows::of).toList());
        return "portal-family";
    }

    @PostMapping("/portal/family/{groupId}/sharing")
    String share(@PathVariable UUID groupId, @RequestParam boolean shares, RedirectAttributes redirect) {
        return onFamily(() -> ownFamily.share(groupId, shares), shares
                ? "Listo: tu familia ahora ve tus turnos."
                : "Listo: tus turnos ya no los ve tu familia.", redirect);
    }

    @PostMapping("/portal/family/{groupId}/leave")
    String leave(@PathVariable UUID groupId, RedirectAttributes redirect) {
        return onFamily(() -> ownFamily.leave(groupId), "Saliste del grupo.", redirect);
    }

    private static String onFamily(Runnable action, String done, RedirectAttributes redirect) {
        try {
            action.run();
            redirect.addFlashAttribute("message", done);
        } catch (FamilyRefusedException | FamilyGroupNotFoundException e) {
            redirect.addFlashAttribute("error", "Ya no estás en ese grupo.");
        }
        return "redirect:/portal/family";
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

    /**
     * One of the patient's appointments, or of a member's they act for (D-85), in Spanish. Never the note nor who
     * cancelled it. {@code cancellable} offers cancelling it, while it is booked and there is time to (D-83).
     */
    record Row(UUID id, String when, String practitioner, String reason, String state, boolean cancellable) {

        static Row of(Appointment appointment, Map<UUID, String> names, boolean cancellable, boolean own) {
            return new Row(appointment.id(), WHEN.format(appointment.startsAt()),
                    names.getOrDefault(appointment.practitionerId(), ""), appointment.reason(),
                    state(appointment, own), cancellable);
        }

        private static String state(Appointment appointment, boolean own) {
            return switch (appointment.state()) {
                case BOOKED -> "Reservado";
                case ABSENT -> own ? "No asististe" : "No asistió";
                case CANCELLED -> appointment.cancellationRequestedBy() == null ? "Cancelado"
                        : switch (appointment.cancellationRequestedBy()) {
                            case PATIENT -> own ? "Cancelado a tu pedido" : "Cancelado a pedido del paciente";
                            case PRACTICE -> "Cancelado por el consultorio";
                        };
            };
        }
    }

    /** A member of the patient's family and their appointments. */
    record MemberRows(String name, List<Row> upcoming, List<Row> past) {
    }

    /** A group of the patient's, in Spanish: each member, how old, whether they share, and which is the patient. */
    record FamilyRows(UUID id, String name, List<MemberRow> members, boolean shares) {

        static FamilyRows of(ViewFamilyUseCase.Group group) {
            ViewFamilyUseCase.Member me = group.members().stream().filter(ViewFamilyUseCase.Member::me).findFirst()
                    .orElseThrow();
            return new FamilyRows(group.id(), group.name(), group.members().stream().map(MemberRow::of).toList(),
                    me.shares());
        }
    }

    record MemberRow(String name, String age, String sharing) {

        static MemberRow of(ViewFamilyUseCase.Member member) {
            return new MemberRow(member.me() ? member.fullName() + " (vos)" : member.fullName(),
                    member.age() == null ? "" : switch (member.age()) {
                        case CHILD -> "menor de 13";
                        case ADOLESCENT -> "de 13 a 17";
                        case ADULT -> "adulto";
                    },
                    member.age() == AgeBand.CHILD ? "Los comparte siempre (menor de 13)"
                            : member.shares() ? "Comparte sus turnos" : "Turnos privados");
        }
    }
}
