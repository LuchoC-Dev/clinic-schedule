package com.clinic.schedule.arrival.session.screen;

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

import com.clinic.schedule.arrival.session.command.AddPatientUseCase;
import com.clinic.schedule.arrival.session.command.AddUnidentifiedPatientUseCase;
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
 * name or document, or registered on the spot without a name — as emergencies or not; and saying
 * that a waiting patient left. Thin: the same commands and reads as the API.
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
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    ArrivalViewController(ListQueuesUseCase queues, ViewQueueSessionUseCase sessions, OpenQueueUseCase open,
            CloseQueueUseCase close, TurnOffAutoCloseUseCase turnOffAutoClose, AddPatientUseCase addPatient,
            DeclareEmergencyUseCase declareEmergency, MarkLeftUseCase markLeft,
            AddUnidentifiedPatientUseCase addUnidentified, PatientReader patients, PractitionerReader practitioners) {
        this.queues = queues;
        this.sessions = sessions;
        this.open = open;
        this.close = close;
        this.turnOffAutoClose = turnOffAutoClose;
        this.addPatient = addPatient;
        this.declareEmergency = declareEmergency;
        this.markLeft = markLeft;
        this.addUnidentified = addUnidentified;
        this.patients = patients;
        this.practitioners = practitioners;
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
            model.addAttribute("called", lines.apply(open.called()));
            model.addAttribute("done", lines.apply(open.done()));
        });
        model.addAttribute("query", query);
        if (query != null && !query.isBlank()) {
            model.addAttribute("found", patients.search(query));
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
            @RequestParam(defaultValue = "false") boolean emergency, RedirectAttributes flash) {
        return attempt(queueId, flash, emergency ? "Paciente sumado como urgencia." : "Paciente sumado a la cola.",
                () -> addPatient.handle(new AddPatientUseCase.Command(queueId, patientId, emergency)));
    }

    /** Nobody is turned away for lack of a name: registered under an alias and brought in (D-60). */
    @PostMapping("/arrivals/{queueId}/unidentified")
    String addUnidentified(@PathVariable UUID queueId, @RequestParam(defaultValue = "false") boolean emergency,
            RedirectAttributes flash) {
        return attempt(queueId, flash, "Paciente sin datos registrado y sumado a la cola.",
                () -> addUnidentified.handle(new AddUnidentifiedPatientUseCase.Command(queueId, emergency)));
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
