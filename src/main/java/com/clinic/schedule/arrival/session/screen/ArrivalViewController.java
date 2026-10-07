package com.clinic.schedule.arrival.session.screen;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.clinic.schedule.arrival.session.command.AddPatientUseCase;
import com.clinic.schedule.appointment.api.AppointmentReader;
import com.clinic.schedule.arrival.session.command.AddUnidentifiedPatientUseCase;
import com.clinic.schedule.arrival.session.command.AppointmentArrivesUseCase;
import com.clinic.schedule.arrival.session.command.LiftReservationUseCase;
import com.clinic.schedule.arrival.session.command.ReassignUseCase;
import com.clinic.schedule.arrival.session.command.CloseQueueUseCase;
import com.clinic.schedule.arrival.session.command.DeclareEmergencyUseCase;
import com.clinic.schedule.arrival.session.command.MarkLeftUseCase;
import com.clinic.schedule.arrival.session.command.OpenQueueUseCase;
import com.clinic.schedule.arrival.session.command.TurnOffAutoCloseUseCase;
import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.query.SessionView;
import com.clinic.schedule.arrival.session.query.ViewQueueSessionUseCase;
import com.clinic.schedule.arrival.setup.query.ListQueuesUseCase;
import com.clinic.schedule.arrival.setup.query.QueueView;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * Reception's screen of the queues at work (D-63): one queue at a time, opened, closed or kept open
 * past its day; who waits in order, who was called and who is done; bringing patients in — found by
 * name or document, or registered on the spot without a name — as emergencies or not, charged in the same
 * step unless an emergency (D-87); and saying that a waiting patient left. Thin: the same commands and reads as the API.
 */
@Controller
class ArrivalViewController {

    private final ListQueuesUseCase queues;
    private final ViewQueueSessionUseCase sessions;
    private final OpenQueueUseCase open;
    private final CloseQueueUseCase close;
    private final TurnOffAutoCloseUseCase turnOffAutoClose;
    private final AddPatientUseCase addPatient;
    private final DeclareEmergencyUseCase declareEmergency;
    private final MarkLeftUseCase markLeft;
    private final AddUnidentifiedPatientUseCase addUnidentified;
    private final AppointmentArrivesUseCase appointmentArrives;
    private final ReassignUseCase reassign;
    private final LiftReservationUseCase liftReservation;
    private final PatientReader patients;
    private final PractitionerReader practitioners;
    private final AppointmentReader appointments;
    private final Clock clock;

    ArrivalViewController(ListQueuesUseCase queues, ViewQueueSessionUseCase sessions, OpenQueueUseCase open,
            CloseQueueUseCase close, TurnOffAutoCloseUseCase turnOffAutoClose, AddPatientUseCase addPatient,
            DeclareEmergencyUseCase declareEmergency, MarkLeftUseCase markLeft,
            AddUnidentifiedPatientUseCase addUnidentified, AppointmentArrivesUseCase appointmentArrives,
            ReassignUseCase reassign, LiftReservationUseCase liftReservation, PatientReader patients,
            PractitionerReader practitioners, AppointmentReader appointments, Clock clock) {
        this.queues = queues;
        this.sessions = sessions;
        this.open = open;
        this.close = close;
        this.turnOffAutoClose = turnOffAutoClose;
        this.addPatient = addPatient;
        this.declareEmergency = declareEmergency;
        this.markLeft = markLeft;
        this.addUnidentified = addUnidentified;
        this.appointmentArrives = appointmentArrives;
        this.reassign = reassign;
        this.liftReservation = liftReservation;
        this.patients = patients;
        this.practitioners = practitioners;
        this.appointments = appointments;
        this.clock = clock;
    }

    @GetMapping("/arrivals")
    String arrivals(@RequestParam(required = false) UUID queueId, @RequestParam(required = false) String query,
            Model model) {
        List<QueueView> all = queues.handle();
        model.addAttribute("queues", all);
        Optional<QueueView> queue = all.stream()
                .filter(each -> each.id().equals(queueId))
                .findFirst()
                .or(() -> all.stream().findFirst());
        if (queue.isEmpty()) {
            return "arrivals";
        }
        model.addAttribute("queue", queue.get());
        Optional<SessionView> session = sessions.handle(queue.get().id());
        model.addAttribute("open", session.isPresent());
        session.ifPresent(open -> {
            Map<UUID, PatientSummary> names = patientsOf(open);
            Map<UUID, String> practitionerNames = practitioners.findAll().stream()
                    .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
            Function<List<Entry>, List<QueueLine>> lines =
                    entries -> entries.stream().map(entry -> QueueLine.of(entry, names, practitionerNames)).toList();
            model.addAttribute("closesByItself", open.closesByItself());
            model.addAttribute("waiting", lines.apply(open.waiting()));
            model.addAttribute("uncallable", lines.apply(open.uncallable()));
            List<Named> serving = queue.get().practitioners().stream()
                    .map(id -> new Named(id, practitionerNames.getOrDefault(id, "(profesional desconocido)")))
                    .toList();
            // Whom each waiting patient may be given to: whoever serves the queue, but the one they wait for.
            model.addAttribute("givable", open.waiting().stream().collect(Collectors.toMap(entry -> entry.id().value(),
                    entry -> serving.stream().filter(named -> !named.id().equals(entry.reservedFor())).toList())));
            model.addAttribute("called", lines.apply(open.called()));
            model.addAttribute("done", lines.apply(open.done()));
        });
        model.addAttribute("query", query);
        if (query != null && !query.isBlank()) {
            LocalDate today = LocalDate.now(clock);
            List<UUID> serving = queue.get().practitioners();
            model.addAttribute("found", patients.search(query).stream()
                    .map(found -> new Found(found, appointments.findNotCancelledForPatientOn(found.id(), today).stream()
                            .filter(appointment -> !appointment.absent() && serving.contains(appointment.practitionerId()))
                            .map(appointment -> new Named(appointment.id(), TIME.format(appointment.startsAt())))
                            .toList()))
                    .toList());
        }
        return "arrivals";
    }

    @PostMapping("/arrivals/{queueId}/open")
    String open(@PathVariable UUID queueId, RedirectAttributes flash) {
        return attempt(queueId, flash, "Cola abierta.", () -> open.handle(new OpenQueueUseCase.Command(queueId)));
    }

    @PostMapping("/arrivals/{queueId}/close")
    String close(@PathVariable UUID queueId, RedirectAttributes flash) {
        return attempt(queueId, flash, "Cola cerrada.", () -> close.handle(new CloseQueueUseCase.Command(queueId)));
    }

    @PostMapping("/arrivals/{queueId}/auto-close-off")
    String turnOffAutoClose(@PathVariable UUID queueId, RedirectAttributes flash) {
        return attempt(queueId, flash, "La cola ya no se cierra sola al terminar el día.",
                () -> turnOffAutoClose.handle(new TurnOffAutoCloseUseCase.Command(queueId)));
    }

    @PostMapping("/arrivals/{queueId}/patients")
    String addPatient(@PathVariable UUID queueId, @RequestParam UUID patientId,
            @RequestParam(defaultValue = "false") boolean emergency, @RequestParam(required = false) BigDecimal amount,
            RedirectAttributes flash) {
        return attempt(queueId, flash, emergency ? "Paciente sumado como urgencia." : "Paciente sumado a la cola.",
                () -> addPatient.handle(new AddPatientUseCase.Command(queueId, patientId, emergency, amount)));
    }

    /** Nobody is turned away for lack of a name: registered under an alias and brought in (D-60). */
    @PostMapping("/arrivals/{queueId}/unidentified")
    String addUnidentified(@PathVariable UUID queueId, @RequestParam(defaultValue = "false") boolean emergency,
            @RequestParam(required = false) BigDecimal amount, RedirectAttributes flash) {
        return attempt(queueId, flash, "Paciente sin datos registrado y sumado a la cola.",
                () -> addUnidentified.handle(new AddUnidentifiedPatientUseCase.Command(queueId, emergency, amount)));
    }

    /**
     * "Llegó" on the agenda: the patient comes into their practitioner's queue, and reception goes
     * back to the agenda it was on (D-65).
     */
    @PostMapping("/arrivals/appointment-arrivals/{appointmentId}")
    String appointmentArrivesFromTheAgenda(@PathVariable UUID appointmentId,
            @RequestParam(required = false) UUID practitionerId, @RequestParam(required = false) String date,
            @RequestParam(required = false) String view, @RequestParam(required = false) BigDecimal amount,
            RedirectAttributes flash) {
        try {
            appointmentArrives.handle(new AppointmentArrivesUseCase.Command(appointmentId, amount));
            flash.addFlashAttribute("message", "Llegó: está en la cola de su profesional.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", ScreenRefusals.of(e));
        }
        UriComponentsBuilder agenda = UriComponentsBuilder.fromPath("/agenda");
        if (practitionerId != null) {
            agenda.queryParam("practitionerId", practitionerId);
        }
        if (date != null && !date.isBlank()) {
            agenda.queryParam("date", date);
        }
        if (view != null && !view.isBlank()) {
            agenda.queryParam("view", view);
        }
        return "redirect:" + agenda.toUriString();
    }

    @PostMapping("/arrivals/{queueId}/appointments/{appointmentId}")
    String appointmentArrives(@PathVariable UUID queueId, @PathVariable UUID appointmentId,
            @RequestParam(required = false) BigDecimal amount, RedirectAttributes flash) {
        return attempt(queueId, flash, "Paciente con turno sumado a la cola de su profesional.",
                () -> appointmentArrives.handle(new AppointmentArrivesUseCase.Command(appointmentId, amount)));
    }

    @PostMapping("/arrivals/{queueId}/entries/{entryId}/reassign")
    String reassign(@PathVariable UUID queueId, @PathVariable UUID entryId, @RequestParam UUID practitionerId,
            RedirectAttributes flash) {
        return attempt(queueId, flash, "Paciente asignado a otro profesional, en el mismo lugar.",
                () -> reassign.handle(new ReassignUseCase.Command(new EntryId(entryId), practitionerId)));
    }

    @PostMapping("/arrivals/{queueId}/entries/{entryId}/lift-reservation")
    String liftReservation(@PathVariable UUID queueId, @PathVariable UUID entryId, RedirectAttributes flash) {
        return attempt(queueId, flash, "Cualquier profesional de la cola puede llamarlo.",
                () -> liftReservation.handle(new LiftReservationUseCase.Command(new EntryId(entryId))));
    }

    @PostMapping("/arrivals/{queueId}/entries/{entryId}/emergency")
    String declareEmergency(@PathVariable UUID queueId, @PathVariable UUID entryId, RedirectAttributes flash) {
        return attempt(queueId, flash, "Marcado como urgencia.",
                () -> declareEmergency.handle(new DeclareEmergencyUseCase.Command(new EntryId(entryId))));
    }

    @PostMapping("/arrivals/{queueId}/entries/{entryId}/left")
    String markLeft(@PathVariable UUID queueId, @PathVariable UUID entryId, RedirectAttributes flash) {
        return attempt(queueId, flash, "Marcado como que se fue sin ser atendido.",
                () -> markLeft.handle(new MarkLeftUseCase.Command(new EntryId(entryId))));
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** Something to choose from a list: its id and what the screen says. */
    record Named(UUID id, String label) {
    }

    /** A patient found by the search, with their appointments today in this queue. */
    record Found(PatientSummary patient, List<Named> appointmentsToday) {
    }

    private Map<UUID, PatientSummary> patientsOf(SessionView session) {
        List<UUID> ids = Stream.of(session.waiting(), session.called(), session.done())
                .flatMap(List::stream).map(Entry::patientId).distinct().toList();
        return patients.findAll(new ArrayList<>(ids)).stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
    }

    private static String attempt(UUID queueId, RedirectAttributes flash, String done, Runnable command) {
        try {
            command.run();
            flash.addFlashAttribute("message", done);
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", ScreenRefusals.of(e));
        }
        return "redirect:/arrivals?queueId=" + queueId;
    }
}
