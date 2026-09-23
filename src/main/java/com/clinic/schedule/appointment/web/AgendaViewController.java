package com.clinic.schedule.appointment.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.clinic.schedule.appointment.application.BookAppointmentUseCase;
import com.clinic.schedule.appointment.application.CancelAppointmentUseCase;
import com.clinic.schedule.appointment.application.CheckInUseCase;
import com.clinic.schedule.appointment.application.FindAppointmentUseCase;
import com.clinic.schedule.appointment.application.RescheduleAppointmentUseCase;
import com.clinic.schedule.appointment.application.SuggestSlotsUseCase;
import com.clinic.schedule.appointment.application.SuspendAutoCancellationUseCase;
import com.clinic.schedule.appointment.application.ViewDayAgendaUseCase;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * Reception's screens over the agenda: the day of a practitioner, booking, moving, and what
 * happens on the day — check-in, a late-arrival notice, cancelling.
 *
 * <p>Thin and throwaway, as D-02 requires: it calls the same use cases the REST controllers
 * call, never the REST controllers themselves, and holds no rule. When the single-page
 * applications arrive, this class and its templates are deleted and nothing else changes.
 *
 * <p>It reads names from the patient and practitioner modules through their {@code api/}.
 * The use cases do not need names, only the screens do, so the screen asks directly instead of
 * widening the application's ports (D-33).
 *
 * <p>Every action answers with a redirect back to the agenda (post/redirect/get), carrying a
 * message, so reloading the page never repeats an operation.
 */
@Controller
class AgendaViewController {

    private final ViewDayAgendaUseCase dayAgenda;
    private final FindAppointmentUseCase findAppointment;
    private final BookAppointmentUseCase book;
    private final RescheduleAppointmentUseCase reschedule;
    private final CancelAppointmentUseCase cancel;
    private final CheckInUseCase checkIn;
    private final SuspendAutoCancellationUseCase suspendAutoCancellation;
    private final SuggestSlotsUseCase suggestSlots;
    private final PractitionerReader practitioners;
    private final PatientReader patients;
    private final Clock clock;

    AgendaViewController(ViewDayAgendaUseCase dayAgenda, FindAppointmentUseCase findAppointment,
            BookAppointmentUseCase book, RescheduleAppointmentUseCase reschedule, CancelAppointmentUseCase cancel,
            CheckInUseCase checkIn, SuspendAutoCancellationUseCase suspendAutoCancellation,
            SuggestSlotsUseCase suggestSlots, PractitionerReader practitioners, PatientReader patients, Clock clock) {
        this.dayAgenda = dayAgenda;
        this.findAppointment = findAppointment;
        this.book = book;
        this.reschedule = reschedule;
        this.cancel = cancel;
        this.checkIn = checkIn;
        this.suspendAutoCancellation = suspendAutoCancellation;
        this.suggestSlots = suggestSlots;
        this.practitioners = practitioners;
        this.patients = patients;
        this.clock = clock;
    }

    @GetMapping("/")
    String home() {
        return "redirect:/agenda";
    }

    // --- the day ------------------------------------------------------------------------

    @GetMapping("/agenda")
    String agenda(@RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        List<PractitionerSummary> all = practitioners.findAll();
        LocalDate day = date != null ? date : LocalDate.now(clock);
        Optional<PractitionerSummary> selected = practitionerId != null
                ? all.stream().filter(practitioner -> practitioner.id().equals(practitionerId)).findFirst()
                : all.stream().findFirst();

        model.addAttribute("practitioners", all);
        model.addAttribute("date", day);
        model.addAttribute("practitioner", selected.orElse(null));
        model.addAttribute("rows", selected.map(practitioner -> rowsOf(practitioner.id(), day)).orElse(List.of()));
        return "agenda";
    }

    private List<AgendaRow> rowsOf(UUID practitionerId, LocalDate day) {
        List<Appointment> appointments = dayAgenda.handle(new PractitionerId(practitionerId), day);
        Map<UUID, PatientSummary> names = patients.findAll(
                        appointments.stream().map(appointment -> appointment.patientId().value()).toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
        return appointments.stream()
                .map(appointment -> AgendaRow.of(appointment, names.get(appointment.patientId().value())))
                .toList();
    }

    @PostMapping("/agenda/appointments/{id}/check-in")
    String checkIn(@PathVariable UUID id, @RequestParam UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, RedirectAttributes flash) {
        attempt(() -> checkIn.handle(new AppointmentId(id)), "Llegada confirmada.", flash);
        return redirectToAgenda(practitionerId, date);
    }

    @PostMapping("/agenda/appointments/{id}/late-notice")
    String lateNotice(@PathVariable UUID id, @RequestParam UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, RedirectAttributes flash) {
        attempt(() -> suspendAutoCancellation.handle(new AppointmentId(id)),
                "Anotado: se lo espera hasta 24 horas antes de cancelar.", flash);
        return redirectToAgenda(practitionerId, date);
    }

    @PostMapping("/agenda/appointments/{id}/cancel")
    String cancel(@PathVariable UUID id, @RequestParam UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, RedirectAttributes flash) {
        attempt(() -> cancel.handle(new AppointmentId(id)), "Turno cancelado.", flash);
        return redirectToAgenda(practitionerId, date);
    }

    // --- booking ------------------------------------------------------------------------

    /**
     * The booking screen, refreshed with GET as reception fills it in: choosing the reason
     * brings the suggested times, searching brings the matching patients.
     */
    @GetMapping("/agenda/new")
    String newAppointment(@RequestParam UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID reasonId,
            @RequestParam(required = false) String patientQuery,
            Model model) {
        PractitionerSummary practitioner = practitioners.findAll().stream()
                .filter(candidate -> candidate.id().equals(practitionerId))
                .findFirst()
                .orElse(null);
        if (practitioner == null) {
            return "redirect:/agenda";
        }
        boolean reasonIsTheirs = reasonId != null
                && practitioner.reasons().stream().anyMatch(reason -> reason.id().equals(reasonId));

        model.addAttribute("practitioner", practitioner);
        model.addAttribute("date", date);
        model.addAttribute("reasonId", reasonIsTheirs ? reasonId : null);
        model.addAttribute("patientQuery", patientQuery);
        model.addAttribute("patients", patientQuery == null ? List.of() : patients.search(patientQuery));
        model.addAttribute("suggestions", reasonIsTheirs
                ? suggestSlots.forReason(new PractitionerId(practitionerId), reasonId, date)
                : List.of());
        return "appointment-new";
    }

    @PostMapping("/agenda/new")
    String book(@RequestParam UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam UUID reasonId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime otherTime,
            @RequestParam(required = false) String note,
            RedirectAttributes flash) {
        LocalTime chosen = otherTime != null ? otherTime : time;
        if (patientId == null || chosen == null) {
            flash.addFlashAttribute("error", "Elegí un paciente y un horario.");
            return "redirect:" + UriComponentsBuilder.fromPath("/agenda/new")
                    .queryParam("practitionerId", practitionerId)
                    .queryParam("date", date)
                    .queryParam("reasonId", reasonId)
                    .toUriString();
        }

        boolean booked = attempt(() -> book.handle(new BookAppointmentUseCase.Command(AppointmentId.newId(),
                        new PatientId(patientId), new PractitionerId(practitionerId), reasonId, date.atTime(chosen),
                        note == null || note.isBlank() ? null : note.strip())),
                "Turno reservado.", flash);
        if (!booked) {
            return "redirect:" + UriComponentsBuilder.fromPath("/agenda/new")
                    .queryParam("practitionerId", practitionerId)
                    .queryParam("date", date)
                    .queryParam("reasonId", reasonId)
                    .toUriString();
        }
        return redirectToAgenda(practitionerId, date);
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

        model.addAttribute("row", AgendaRow.of(appointment, patient));
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
        return redirectToAgenda(practitionerId, date);
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

    private static String redirectToAgenda(UUID practitionerId, LocalDate date) {
        return "redirect:" + UriComponentsBuilder.fromPath("/agenda")
                .queryParam("practitionerId", practitionerId)
                .queryParam("date", date)
                .toUriString();
    }
}
