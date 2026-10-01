package com.clinic.schedule.appointment.adapter.in.screen;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.clinic.schedule.appointment.application.exception.AgendaAccessDeniedException;
import com.clinic.schedule.appointment.application.usecase.BookAppointmentUseCase;
import com.clinic.schedule.appointment.application.usecase.BookNewPatientUseCase;
import com.clinic.schedule.appointment.application.usecase.CancelAppointmentUseCase;
import com.clinic.schedule.appointment.application.usecase.FindArrivalsUseCase;
import com.clinic.schedule.appointment.application.usecase.FindAppointmentUseCase;
import com.clinic.schedule.appointment.application.usecase.RescheduleAppointmentUseCase;
import com.clinic.schedule.appointment.application.usecase.SuggestSlotsUseCase;
import com.clinic.schedule.appointment.application.usecase.RecordLateArrivalNoticeUseCase;
import com.clinic.schedule.appointment.application.usecase.ViewAgendaUseCase;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.CancellationRequester;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientRegistrationRefusedException;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;
import com.clinic.schedule.practitioner.api.ReasonDetails;
import com.clinic.schedule.security.api.CurrentUserReader;

/**
 * Reception's screens over the agenda: a day or a week, of one practitioner or all of them;
 * booking, moving, and what happens on the day — a late-arrival notice, cancelling. The arrival is
 * the queues': "Llegó" posts to them (D-65).
 *
 * <p>Thin and throwaway, as D-02 requires: it calls the same use cases the REST controllers
 * call, never the REST controllers themselves, and holds no rule. When the single-page
 * applications arrive, this class and its templates are deleted and nothing else changes.
 *
 * <p>It reads names from the patient and practitioner modules, books the new patients a
 * booking brings through the use case that registers them (D-37, D-75), and asks the security
 * module who is looking, all through their {@code api/}.
 * The use cases do not need names, only the screens do, so the screen asks directly instead of
 * widening the application's ports (D-33).
 *
 * <p>Every action answers with a redirect back to the agenda as it was being viewed
 * (post/redirect/get), carrying a message, so reloading the page never repeats an operation.
 */
@Controller
class AgendaViewController {

    private static final String WEEK = "week";

    private final ViewAgendaUseCase agenda;
    private final FindAppointmentUseCase findAppointment;
    private final BookAppointmentUseCase book;
    private final RescheduleAppointmentUseCase reschedule;
    private final CancelAppointmentUseCase cancel;
    private final FindArrivalsUseCase arrivals;
    private final RecordLateArrivalNoticeUseCase recordLateArrivalNotice;
    private final SuggestSlotsUseCase suggestSlots;
    private final PractitionerReader practitioners;
    private final PatientReader patients;
    private final BookNewPatientUseCase bookNewPatient;
    private final CurrentUserReader currentUser;
    private final Clock clock;

    AgendaViewController(ViewAgendaUseCase agenda, FindAppointmentUseCase findAppointment,
            BookAppointmentUseCase book, RescheduleAppointmentUseCase reschedule, CancelAppointmentUseCase cancel,
            FindArrivalsUseCase arrivals, RecordLateArrivalNoticeUseCase recordLateArrivalNotice,
            SuggestSlotsUseCase suggestSlots, PractitionerReader practitioners, PatientReader patients,
            BookNewPatientUseCase bookNewPatient, CurrentUserReader currentUser, Clock clock) {
        this.agenda = agenda;
        this.findAppointment = findAppointment;
        this.book = book;
        this.reschedule = reschedule;
        this.cancel = cancel;
        this.arrivals = arrivals;
        this.recordLateArrivalNotice = recordLateArrivalNotice;
        this.suggestSlots = suggestSlots;
        this.practitioners = practitioners;
        this.patients = patients;
        this.bookNewPatient = bookNewPatient;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping("/")
    String home() {
        return "redirect:/agenda";
    }

    // --- the agenda ---------------------------------------------------------------------

    /**
     * The agenda of a day or of the week around it, Monday to Sunday. Without a practitioner
     * it shows everyone's, with a column saying whose each appointment is.
     *
     * <p>A practitioner sees only their own, read-only: whose agenda it is comes back from the
     * use case, and the choice of practitioner and the buttons are hidden, since the routes
     * behind them are closed to them anyway (D-34). Asking for someone else's answers 403 (D-39).
     * What they get instead are links into the clinical record module's screens — a patient's
     * record, and "Empezar consulta" once the patient arrived — which are only URLs: this module
     * does not depend on that one (D-51).
     */
    @GetMapping("/agenda")
    String agenda(@RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String view,
            Model model) {
        Optional<UUID> restrictedTo = currentUser.practitionerId();
        List<PractitionerSummary> everyone = practitioners.findAll();
        List<PractitionerSummary> selectable = everyone.stream()
                .filter(practitioner -> restrictedTo.map(practitioner.id()::equals).orElse(true))
                .toList();
        LocalDate day = date != null ? date : LocalDate.now(clock);
        boolean week = WEEK.equals(view);
        LocalDate from = week ? day.with(DayOfWeek.MONDAY) : day;
        LocalDate to = week ? from.plusDays(6) : day;

        ViewAgendaUseCase.Agenda read;
        try {
            read = agenda.handle(Optional.ofNullable(practitionerId).map(PractitionerId::new), from, to);
        } catch (AgendaAccessDeniedException e) {
            // Only reachable by editing the URL: a practitioner is offered no one else to pick (D-39).
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
        PractitionerSummary shown = read.practitionerId()
                .flatMap(whose -> everyone.stream()
                        .filter(practitioner -> practitioner.id().equals(whose.value()))
                        .findFirst())
                .orElse(null);
        List<AgendaRow> rows = rowsOf(read.appointments(), everyone);

        List<AgendaDay> days = new ArrayList<>();
        for (LocalDate each = from; !each.isAfter(to); each = each.plusDays(1)) {
            LocalDate current = each;
            days.add(AgendaDay.of(current, rows.stream().filter(row -> row.date().equals(current)).toList()));
        }

        model.addAttribute("practitioners", selectable);
        model.addAttribute("practitioner", shown);
        model.addAttribute("everyone", read.practitionerId().isEmpty());
        model.addAttribute("date", day);
        model.addAttribute("view", week ? WEEK : "day");
        model.addAttribute("days", days);
        model.addAttribute("previous", week ? day.minusWeeks(1) : day.minusDays(1));
        model.addAttribute("next", week ? day.plusWeeks(1) : day.plusDays(1));
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("readOnly", restrictedTo.isPresent());
        return "agenda";
    }

    private List<AgendaRow> rowsOf(List<Appointment> appointments, List<PractitionerSummary> everyone) {
        Map<UUID, PatientSummary> patientNames = patients.findAll(
                        appointments.stream().map(appointment -> appointment.patientId().value()).toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        Map<UUID, String> practitionerNames = everyone.stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
        Map<AppointmentId, LocalDateTime> arrived = arrivals.handle(appointments.stream().map(Appointment::id).toList());
        return appointments.stream()
                .map(appointment -> AgendaRow.of(appointment,
                        patientNames.get(appointment.patientId().value()),
                        practitionerNames.get(appointment.practitionerId().value()),
                        arrived.get(appointment.id())))
                .toList();
    }


    @PostMapping("/agenda/appointments/{id}/late-notice")
    String lateNotice(@PathVariable UUID id, @RequestParam(required = false) UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String view, RedirectAttributes flash) {
        attempt(() -> recordLateArrivalNotice.handle(new AppointmentId(id)),
                "Anotado: se lo espera hasta el final del día.", flash);
        return redirectToAgenda(practitionerId, date, view);
    }

    /** Reception says who asked for it, and may leave a note (D-76). */
    @PostMapping("/agenda/appointments/{id}/cancel")
    String cancel(@PathVariable UUID id, @RequestParam(required = false) UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) CancellationRequester requestedBy,
            @RequestParam(required = false) String note, RedirectAttributes flash) {
        if (requestedBy == null) {
            flash.addFlashAttribute("error", "Para cancelar, elegí quién lo pidió: el paciente o el consultorio.");
        } else {
            attempt(() -> cancel.handle(new CancelAppointmentUseCase.Command(new AppointmentId(id), requestedBy, note)),
                    "Turno cancelado.", flash);
        }
        return redirectToAgenda(practitionerId, date, view);
    }

    // --- booking ------------------------------------------------------------------------

    /**
     * The booking screen, refreshed as reception fills it in: choosing the practitioner brings
     * their reasons, choosing the reason brings the suggested times, searching brings the
     * matching patients, and whatever was already chosen stays chosen. The practitioner is one
     * more choice on the screen: it comes chosen from a practitioner's agenda and is to be chosen
     * from everyone's (D-45). Their first reason comes chosen, so the free times show at once
     * (D-36).
     */
    @GetMapping("/agenda/new")
    String newAppointment(@ModelAttribute("form") BookingScreenRequest form, Model model) {
        if (form.date() == null) {
            return "redirect:/agenda";
        }
        List<PractitionerSummary> everyone = practitioners.findAll();
        PractitionerSummary practitioner = form.practitionerId() == null ? null
                : everyone.stream()
                        .filter(candidate -> candidate.id().equals(form.practitionerId()))
                        .findFirst()
                        .orElse(null);
        // A reason of the practitioner chosen before is not one of this one's: it falls back to their first.
        UUID reasonId = practitioner == null ? null : practitioner.reasons().stream()
                .map(ReasonDetails::id)
                .filter(id -> id.equals(form.reasonId()))
                .findFirst()
                .or(() -> practitioner.reasons().stream().map(ReasonDetails::id).findFirst())
                .orElse(null);

        List<PatientSummary> found = form.patientQuery() == null || form.patientQuery().isBlank()
                ? List.of() : patients.search(form.patientQuery());
        // The patient already chosen stays on screen even when the search no longer finds them.
        List<PatientSummary> shown = new ArrayList<>(found);
        if (form.patientId() != null && found.stream().noneMatch(patient -> patient.id().equals(form.patientId()))) {
            shown.addAll(0, patients.findAll(List.of(form.patientId())));
        }
        // The new patient typed so far comes back from the session, never from the address (D-75).
        NewPatientForm newPatient = model.getAttribute("newPatient") instanceof NewPatientForm typed
                ? typed : NewPatientForm.NONE;
        UUID chosenPatient = form.patientId() != null ? form.patientId()
                : !newPatient.given() && shown.size() == 1 ? shown.getFirst().id() : null;

        model.addAttribute("practitioners", everyone);
        model.addAttribute("practitioner", practitioner);
        model.addAttribute("reasonId", reasonId);
        model.addAttribute("patients", shown);
        model.addAttribute("chosenPatient", chosenPatient);
        model.addAttribute("newPatient", newPatient);
        model.addAttribute("documentTypes", patients.documentTypes());
        model.addAttribute("relations", NewPatientForm.relations(patients.relations()));
        model.addAttribute("suggestions", reasonId != null
                ? suggestSlots.forReason(new PractitionerId(practitioner.id()), reasonId, form.date())
                : null);
        return "appointment-new";
    }

    /**
     * The booking form has two buttons. "Actualizar" only carries the form back to the screen,
     * so a search never loses what was chosen; "Reservar" books.
     *
     * <p>A new patient is registered and booked in one transaction (D-37, D-75): if the booking is
     * refused, nobody is registered, and the screen comes back with the patient still typed in, to try
     * another time.
     */
    @PostMapping("/agenda/new")
    String book(@ModelAttribute BookingScreenRequest form, @RequestParam(defaultValue = "book") String action,
            RedirectAttributes flash) {
        if (form.date() == null) {
            return "redirect:/agenda";
        }
        if ("refresh".equals(action)) {
            return backToScreen(form, flash);
        }
        String missing = form.missing();
        if (missing != null) {
            flash.addFlashAttribute("error", missing);
            return backToScreen(form, flash);
        }
        if (form.hasTwoPatients()) {
            flash.addFlashAttribute("error", "Elegiste un paciente y también cargaste uno nuevo: dejá solo uno.");
            return backToScreen(form, flash);
        }

        boolean booked;
        if (form.hasNewPatient()) {
            booked = attempt(() -> bookNewPatient.handle(new BookNewPatientUseCase.Command(AppointmentId.newId(),
                            form.newPatient().toPatient(), new PractitionerId(form.practitionerId()),
                            form.reasonId(), form.date().atTime(form.chosenTime()), form.cleanNote())),
                    "Paciente registrado y turno reservado.", flash);
        } else {
            booked = attempt(() -> book.handle(new BookAppointmentUseCase.Command(AppointmentId.newId(),
                            new PatientId(form.patientId()), new PractitionerId(form.practitionerId()),
                            form.reasonId(), form.date().atTime(form.chosenTime()), form.cleanNote())),
                    "Turno reservado.", flash);
        }
        if (!booked) {
            return backToScreen(form, flash);
        }
        return redirectToAgenda(form.practitionerId(), form.date(), null);
    }

    /** The booking screen again, with the new patient typed so far kept in the session, not the address (D-75). */
    private static String backToScreen(BookingScreenRequest form, RedirectAttributes flash) {
        if (form.hasNewPatient()) {
            flash.addFlashAttribute("newPatient", form.newPatient());
        }
        return "redirect:" + form.toScreenUrl();
    }

    // --- moving -------------------------------------------------------------------------

    @GetMapping("/agenda/appointments/{id}/reschedule")
    String moveScreen(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model, RedirectAttributes flash) {
        Appointment appointment;
        try {
            appointment = findAppointment.handle(new AppointmentId(id));
        } catch (RuntimeException e) {
            if (!ScreenMessages.isExpected(e)) {
                throw e;
            }
            flash.addFlashAttribute("error", ScreenMessages.of(e));
            return "redirect:/agenda";
        }
        LocalDate day = date != null ? date : appointment.slot().startsAt().toLocalDate();
        PatientSummary patient = patients.findAll(List.of(appointment.patientId().value())).stream()
                .findFirst().orElse(null);
        String practitionerName = practitioners.findAll().stream()
                .filter(practitioner -> practitioner.id().equals(appointment.practitionerId().value()))
                .map(PractitionerSummary::fullName)
                .findFirst().orElse(null);

        model.addAttribute("row", AgendaRow.of(appointment, patient, practitionerName,
                arrivals.handle(List.of(appointment.id())).get(appointment.id())));
        model.addAttribute("practitionerId", appointment.practitionerId().value());
        model.addAttribute("currentDate", appointment.slot().startsAt().toLocalDate());
        model.addAttribute("date", day);
        model.addAttribute("suggestions", suggestSlots.forMoving(appointment, day));
        return "appointment-reschedule";
    }

    @PostMapping("/agenda/appointments/{id}/reschedule")
    String move(@PathVariable UUID id, @RequestParam UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime otherTime,
            RedirectAttributes flash) {
        LocalTime chosen = otherTime != null ? otherTime : time;
        if (chosen == null) {
            flash.addFlashAttribute("error", "Elegí un horario.");
            return "redirect:/agenda/appointments/" + id + "/reschedule?date=" + date;
        }
        boolean moved = attempt(
                () -> reschedule.handle(new RescheduleAppointmentUseCase.Command(new AppointmentId(id), date.atTime(chosen))),
                "Turno movido.", flash);
        if (!moved) {
            return "redirect:/agenda/appointments/" + id + "/reschedule?date=" + date;
        }
        return redirectToAgenda(practitionerId, date, null);
    }

    // --- helpers ------------------------------------------------------------------------

    /**
     * Runs an operation and leaves a message for the next screen. Refusals the rules expect
     * become a message in Spanish; anything else is a real failure and is not hidden.
     *
     * @return whether it went through
     */
    private boolean attempt(Runnable operation, String success, RedirectAttributes flash) {
        try {
            operation.run();
            flash.addFlashAttribute("message", success);
            return true;
        } catch (PatientRegistrationRefusedException e) {
            flash.addFlashAttribute("error", ScreenMessages.of(e));
            return false;
        } catch (RuntimeException e) {
            if (!ScreenMessages.isExpected(e)) {
                throw e;
            }
            flash.addFlashAttribute("error", ScreenMessages.of(e));
            return false;
        }
    }

    /** Back to the agenda as it was being viewed: no practitioner means everyone's. */
    private static String redirectToAgenda(UUID practitionerId, LocalDate date, String view) {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/agenda");
        if (practitionerId != null) {
            url.queryParam("practitionerId", practitionerId);
        }
        url.queryParam("date", date);
        if (WEEK.equals(view)) {
            url.queryParam("view", WEEK);
        }
        return "redirect:" + url.toUriString();
    }
}
