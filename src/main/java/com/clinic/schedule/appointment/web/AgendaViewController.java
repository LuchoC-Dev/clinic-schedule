package com.clinic.schedule.appointment.web;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
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

import com.clinic.schedule.appointment.application.AgendaAccessDeniedException;
import com.clinic.schedule.appointment.application.BookAppointmentUseCase;
import com.clinic.schedule.appointment.application.CancelAppointmentUseCase;
import com.clinic.schedule.appointment.application.CheckInUseCase;
import com.clinic.schedule.appointment.application.FindAppointmentUseCase;
import com.clinic.schedule.appointment.application.RescheduleAppointmentUseCase;
import com.clinic.schedule.appointment.application.SuggestSlotsUseCase;
import com.clinic.schedule.appointment.application.SuspendAutoCancellationUseCase;
import com.clinic.schedule.appointment.application.ViewAgendaUseCase;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientRegistrationRefusedException;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.patient.api.PatientWriter;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;
import com.clinic.schedule.practitioner.api.ReasonDetails;
import com.clinic.schedule.security.api.CurrentUserReader;

/**
 * Reception's screens over the agenda: a day or a week, of one practitioner or all of them;
 * booking, moving, and what happens on the day — check-in, a late-arrival notice, cancelling.
 *
 * <p>Thin and throwaway, as D-02 requires: it calls the same use cases the REST controllers
 * call, never the REST controllers themselves, and holds no rule. When the single-page
 * applications arrive, this class and its templates are deleted and nothing else changes.
 *
 * <p>It reads names from the patient and practitioner modules, registers the new patients a
 * booking brings (D-37), and asks the security module who is looking, all through their
 * {@code api/}.
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
    private final CheckInUseCase checkIn;
    private final SuspendAutoCancellationUseCase suspendAutoCancellation;
    private final SuggestSlotsUseCase suggestSlots;
    private final PractitionerReader practitioners;
    private final PatientReader patients;
    private final PatientWriter newPatients;
    private final CurrentUserReader currentUser;
    private final Clock clock;

    AgendaViewController(ViewAgendaUseCase agenda, FindAppointmentUseCase findAppointment,
            BookAppointmentUseCase book, RescheduleAppointmentUseCase reschedule, CancelAppointmentUseCase cancel,
            CheckInUseCase checkIn, SuspendAutoCancellationUseCase suspendAutoCancellation,
            SuggestSlotsUseCase suggestSlots, PractitionerReader practitioners, PatientReader patients,
            PatientWriter newPatients, CurrentUserReader currentUser, Clock clock) {
        this.agenda = agenda;
        this.findAppointment = findAppointment;
        this.book = book;
        this.reschedule = reschedule;
        this.cancel = cancel;
        this.checkIn = checkIn;
        this.suspendAutoCancellation = suspendAutoCancellation;
        this.suggestSlots = suggestSlots;
        this.practitioners = practitioners;
        this.patients = patients;
        this.newPatients = newPatients;
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
        return appointments.stream()
                .map(appointment -> AgendaRow.of(appointment,
                        patientNames.get(appointment.patientId().value()),
                        practitionerNames.get(appointment.practitionerId().value())))
                .toList();
    }

    @PostMapping("/agenda/appointments/{id}/check-in")
    String checkIn(@PathVariable UUID id, @RequestParam(required = false) UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String view, RedirectAttributes flash) {
        attempt(() -> checkIn.handle(new AppointmentId(id)), "Llegada confirmada.", flash);
        return redirectToAgenda(practitionerId, date, view);
    }

    @PostMapping("/agenda/appointments/{id}/late-notice")
    String lateNotice(@PathVariable UUID id, @RequestParam(required = false) UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String view, RedirectAttributes flash) {
        attempt(() -> suspendAutoCancellation.handle(new AppointmentId(id)),
                "Anotado: se lo espera hasta 24 horas antes de cancelar.", flash);
        return redirectToAgenda(practitionerId, date, view);
    }

    @PostMapping("/agenda/appointments/{id}/cancel")
    String cancel(@PathVariable UUID id, @RequestParam(required = false) UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String view, RedirectAttributes flash) {
        attempt(() -> cancel.handle(new AppointmentId(id)), "Turno cancelado.", flash);
        return redirectToAgenda(practitionerId, date, view);
    }

    // --- booking ------------------------------------------------------------------------

    /**
     * The booking screen, refreshed as reception fills it in: choosing the reason brings the
     * suggested times, searching brings the matching patients, and whatever was already chosen
     * stays chosen. The first reason comes chosen, so the free times show from the start (D-36).
     */
    @GetMapping("/agenda/new")
    String newAppointment(@ModelAttribute("form") BookingScreenRequest form, Model model) {
        PractitionerSummary practitioner = form.practitionerId() == null ? null
                : practitioners.findAll().stream()
                        .filter(candidate -> candidate.id().equals(form.practitionerId()))
                        .findFirst()
                        .orElse(null);
        if (practitioner == null || form.date() == null) {
            return "redirect:/agenda";
        }
        UUID reasonId = practitioner.reasons().stream()
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
        UUID chosenPatient = form.patientId() != null ? form.patientId()
                : !form.hasNewPatient() && shown.size() == 1 ? shown.getFirst().id() : null;

        model.addAttribute("practitioner", practitioner);
        model.addAttribute("reasonId", reasonId);
        model.addAttribute("patients", shown);
        model.addAttribute("chosenPatient", chosenPatient);
        model.addAttribute("documentTypes", patients.documentTypes());
        model.addAttribute("suggestions", reasonId != null
                ? suggestSlots.forReason(new PractitionerId(practitioner.id()), reasonId, form.date())
                : List.of());
        return "appointment-new";
    }

    /**
     * The booking form has two buttons. "Actualizar" only carries the form back to the screen,
     * so a search never loses what was chosen; "Reservar" books.
     *
     * <p>A new patient is registered first and booked afterwards, two operations with a
     * transaction each (D-37). If the booking is then refused, the patient stays registered and
     * comes back as the chosen one, so trying another time does not register them twice.
     */
    @PostMapping("/agenda/new")
    String book(@ModelAttribute BookingScreenRequest form, @RequestParam(defaultValue = "book") String action,
            RedirectAttributes flash) {
        if (form.practitionerId() == null || form.date() == null) {
            return "redirect:/agenda";
        }
        if ("refresh".equals(action)) {
            return "redirect:" + form.toScreenUrl();
        }
        String missing = form.missing();
        if (missing != null) {
            flash.addFlashAttribute("error", missing);
            return "redirect:" + form.toScreenUrl();
        }
        if (form.hasTwoPatients()) {
            flash.addFlashAttribute("error", "Elegiste un paciente y también cargaste uno nuevo: dejá solo uno.");
            return "redirect:" + form.toScreenUrl();
        }

        BookingScreenRequest booking = form;
        if (form.hasNewPatient()) {
            try {
                booking = form.withPatient(newPatients.register(form.newPatientName(),
                        form.newPatientDocumentType(), form.newPatientDocumentNumber()));
            } catch (PatientRegistrationRefusedException e) {
                flash.addFlashAttribute("error", ScreenMessages.of(e));
                return "redirect:" + form.toScreenUrl();
            }
        }

        BookingScreenRequest chosen = booking;
        boolean booked = attempt(() -> book.handle(new BookAppointmentUseCase.Command(AppointmentId.newId(),
                        new PatientId(chosen.patientId()), new PractitionerId(chosen.practitionerId()),
                        chosen.reasonId(), chosen.date().atTime(chosen.chosenTime()), chosen.cleanNote())),
                form.hasNewPatient() ? "Paciente registrado y turno reservado." : "Turno reservado.", flash);
        if (!booked) {
            if (form.hasNewPatient()) {
                flash.addFlashAttribute("message", "El paciente quedó registrado y elegido; falta el turno.");
            }
            return "redirect:" + chosen.toScreenUrl();
        }
        return redirectToAgenda(chosen.practitionerId(), chosen.date(), null);
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

        model.addAttribute("row", AgendaRow.of(appointment, patient, null));
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
