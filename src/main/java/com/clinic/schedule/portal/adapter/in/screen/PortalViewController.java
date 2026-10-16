package com.clinic.schedule.portal.adapter.in.screen;

import com.clinic.schedule.portal.application.usecase.RecoverPasswordUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnPasswordUseCase;
import com.clinic.schedule.portal.application.exception.OwnPasswordRefusedException;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
import org.springframework.web.util.UriComponentsBuilder;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.portal.application.exception.NotYourAppointmentException;
import com.clinic.schedule.portal.application.exception.OwnDataRefusedException;
import com.clinic.schedule.portal.application.usecase.BookOnWebUseCase;
import com.clinic.schedule.portal.application.usecase.CancelOwnAppointmentUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnConsentUseCase;
import com.clinic.schedule.portal.application.usecase.ChangeOwnContactUseCase;
import com.clinic.schedule.portal.application.usecase.ListOwnAppointmentsUseCase;
import com.clinic.schedule.portal.application.usecase.MoveOwnAppointmentUseCase;
import com.clinic.schedule.portal.application.usecase.ViewOwnDataUseCase;
import com.clinic.schedule.portal.application.usecase.ViewWebBookingUseCase;
import com.clinic.schedule.portal.domain.Appointment;
import com.clinic.schedule.portal.domain.BookableReason;
import com.clinic.schedule.portal.domain.OwnContact;
import com.clinic.schedule.portal.domain.OwnData;
import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException;
import com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException;
import com.clinic.schedule.portal.domain.exception.WebMoveRefusedException;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The patients' screens (D-80), in Spanish, since patients read them (D-13): logging in with their document,
 * their appointments, their data, which they change themselves (D-81), booking an appointment (D-82), moving one
 * to another time (D-93) and cancelling one (D-83), and their family: the appointments of the members they act
 * for, booked, moved and cancelled as their own, and the groups they are in (D-85); and their password, changed
 * or asked for by email (D-86). Thin and throwaway, as every screen (D-02): the patients' single-page application
 * replaces them, part by part. The addresses the emails carry — activating an account, choosing a new password,
 * cancelling from a link, *Mis turnos* — already send on to it (D-110, D-111).
 */
@Controller
class PortalViewController {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d/MM/yyyy");
    private static final DateTimeFormatter DAY_NAME =
            DateTimeFormatter.ofPattern("EEEE d/MM/yyyy", Locale.forLanguageTag("es-AR"));

    private final ListOwnAppointmentsUseCase appointments;
    private final ViewOwnDataUseCase data;
    private final ChangeOwnContactUseCase contact;
    private final ChangeOwnConsentUseCase consent;
    private final ViewWebBookingUseCase booking;
    private final BookOnWebUseCase book;
    private final CancelOwnAppointmentUseCase cancel;
    private final MoveOwnAppointmentUseCase move;
    private final ViewFamilyUseCase family;
    private final ManageOwnFamilyUseCase ownFamily;
    private final RecoverPasswordUseCase recovery;
    private final ChangeOwnPasswordUseCase ownPassword;
    private final PatientReader patients;
    private final PractitionerReader practitioners;
    private final Clock clock;

    PortalViewController(ListOwnAppointmentsUseCase appointments,
            ViewOwnDataUseCase data, ChangeOwnContactUseCase contact, ChangeOwnConsentUseCase consent,
            ViewWebBookingUseCase booking, BookOnWebUseCase book, CancelOwnAppointmentUseCase cancel,
            MoveOwnAppointmentUseCase move, ViewFamilyUseCase family,
            ManageOwnFamilyUseCase ownFamily,
            RecoverPasswordUseCase recovery, ChangeOwnPasswordUseCase ownPassword, PatientReader patients,
            PractitionerReader practitioners, Clock clock) {
        this.appointments = appointments;
        this.data = data;
        this.contact = contact;
        this.consent = consent;
        this.booking = booking;
        this.book = book;
        this.cancel = cancel;
        this.move = move;
        this.family = family;
        this.ownFamily = ownFamily;
        this.recovery = recovery;
        this.ownPassword = ownPassword;
        this.patients = patients;
        this.practitioners = practitioners;
        this.clock = clock;
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

    /**
     * The address of the activation's email, which emails already sent carry: the patients' application has the
     * screen now (D-110).
     */
    @GetMapping("/portal/activation")
    String activation(@RequestParam(required = false) String token) {
        return toApplication("/activation", token);
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

    /** The address of the recovery's email, sent on to the patients' application (D-110). */
    @GetMapping("/portal/password-reset")
    String passwordReset(@RequestParam(required = false) String token) {
        return toApplication("/password-reset", token);
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

    /**
     * *Mis turnos*, which the booking's email points at and the login lands on, is the patients' application's now
     * (D-111).
     */
    @GetMapping("/portal/appointments")
    String appointments() {
        return toApplication("/appointments", null);
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

    /**
     * Moving one of their appointments, or a member's, in steps that come back to the same screen, as booking: a
     * new day, then the times the agenda offers to move it to that day, each a button that moves it (D-93).
     */
    @GetMapping("/portal/appointments/{id}/move")
    String moving(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        MoveOwnAppointmentUseCase.Target target;
        try {
            target = move.find(id);
        } catch (NotYourAppointmentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
        Appointment appointment = target.appointment();
        model.addAttribute("id", id);
        model.addAttribute("member", target.member());
        model.addAttribute("when", WHEN.format(appointment.startsAt()));
        model.addAttribute("practitioner", practitioners.findAll().stream()
                .filter(practitioner -> practitioner.id().equals(appointment.practitionerId()))
                .map(PractitionerSummary::fullName)
                .findFirst()
                .orElse(""));
        model.addAttribute("reason", appointment.reason());
        model.addAttribute("movable", target.movable());
        model.addAttribute("firstDay", target.firstDay());
        model.addAttribute("lastDay", target.lastDay());
        model.addAttribute("lastDayLabel", DAY.format(target.lastDay()));
        model.addAttribute("date", date == null ? target.firstDay() : date);
        if (target.movable() && date != null) {
            try {
                model.addAttribute("day", DAY_NAME.format(date));
                model.addAttribute("times", move.times(id, date));
            } catch (WebMoveRefusedException e) {
                model.addAttribute("error", moveRefusal(e));
            }
        }
        return "portal-move";
    }

    @PostMapping("/portal/appointments/{id}/move")
    String move(@PathVariable UUID id, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startsAt, RedirectAttributes redirect) {
        try {
            MoveOwnAppointmentUseCase.Result moved = move.handle(id, startsAt);
            redirect.addFlashAttribute("message", "Turno cambiado"
                    + (moved.member() == null ? "" : " para " + moved.member()) + ": "
                    + WHEN.format(moved.startsAt()) + " con " + moved.practitioner() + (moved.confirmationSent()
                            ? ". Te mandamos la confirmación por email."
                            : ". No pudimos mandarte la confirmación por email, pero el turno quedó cambiado."));
            return "redirect:/portal/appointments";
        } catch (NotYourAppointmentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (WebMoveRefusedException e) {
            redirect.addFlashAttribute("error", moveRefusal(e));
            return "redirect:/portal/appointments/" + id + "/move?date=" + startsAt.toLocalDate();
        }
    }

    /** What the patient reads when an appointment cannot be moved on the web where they asked. */
    private static String moveRefusal(WebMoveRefusedException e) {
        return switch (e.reason()) {
            case NOT_BOOKED -> "Ese turno ya no está reservado.";
            case TOO_LATE -> "Falta menos de un día para el turno: para cambiarlo, llamá o acercate a recepción.";
            case DAY_OUT_OF_RANGE ->
                    "Ese día no se puede elegir por la web: elegí uno entre mañana y el último que ofrece la pantalla.";
            case SAME_TIME -> "El turno ya es a esa hora: elegí otra.";
            case NOT_AN_OFFERED_TIME, TIME_TAKEN -> "Ese horario ya no está libre: elegí otro.";
            case PATIENT_BUSY -> "Ya hay otro turno del paciente a esa hora: elegí otra.";
        };
    }

    /** The address of the reminders' and the confirmations' links, sent on to the patients' application (D-110). */
    @GetMapping("/portal/cancel")
    String cancelLink(@RequestParam(required = false) String token) {
        return toApplication("/cancel", token);
    }

    /**
     * Where an email's address goes now: the same path on the patients' application, with the link's token, so an
     * email already sent keeps working (D-110). Once the application is at {@code /portal/} (D-97), the address is
     * its own.
     */
    static String toApplication(String path, String token) {
        return "redirect:" + UriComponentsBuilder.fromPath("/portal/app" + path)
                .queryParamIfPresent("token", Optional.ofNullable(token))
                .encode()
                .build()
                .toUriString();
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
            case ONE_AHEAD_UNTIL_VERIFIED -> "Hasta que recepción verifique tu identidad podés tener un turno por "
                    + "delante reservado por la web. Acercate con tu documento, o llamá para sacar otro.";
            case PATIENT_BUSY -> "Ya tenés otro turno a esa hora.";
        };
    }

    /** The patient's groups: who is in each, who shares, and the patient's own choices (D-85). */
    @GetMapping("/portal/family")
    String family(Model model) {
        model.addAttribute("groups", family.groups().stream().map(FamilyRows::of).toList());
        AccountHolder me = family.bookableFor().get(0);
        model.addAttribute("adult", me.birthDate() != null
                && AgeBand.of(me.birthDate(), LocalDate.now(clock)) == AgeBand.ADULT);
        model.addAttribute("documentTypes", patients.documentTypes());
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
