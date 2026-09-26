package com.clinic.schedule.arrival.setup.screen;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.arrival.eventstore.StreamChangedException;
import com.clinic.schedule.arrival.setup.QueueNotFoundException;
import com.clinic.schedule.arrival.setup.command.AddPractitionerUseCase;
import com.clinic.schedule.arrival.setup.command.CreateQueueUseCase;
import com.clinic.schedule.arrival.setup.command.RemovePractitionerUseCase;
import com.clinic.schedule.arrival.setup.command.RenameQueueUseCase;
import com.clinic.schedule.arrival.setup.command.UnknownPractitionerException;
import com.clinic.schedule.arrival.setup.domain.QueueId;
import com.clinic.schedule.arrival.setup.domain.exception.PractitionerInAnotherQueueException;
import com.clinic.schedule.arrival.setup.domain.exception.QueueNameTakenException;
import com.clinic.schedule.arrival.setup.domain.exception.QueueRuleViolationException;
import com.clinic.schedule.arrival.setup.query.FindQueueUseCase;
import com.clinic.schedule.arrival.setup.query.ListQueuesUseCase;
import com.clinic.schedule.arrival.setup.query.QueueView;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * Reception's queue screens: every queue with who serves it, setting one up, and each queue's own
 * screen to rename it and to add or take out its practitioners (D-62). Thin, like every screen
 * (D-02): it runs the same commands and reads as the API, and says their refusals in Spanish.
 */
@Controller
class QueueViewController {

    private final CreateQueueUseCase create;
    private final RenameQueueUseCase rename;
    private final AddPractitionerUseCase addPractitioner;
    private final RemovePractitionerUseCase removePractitioner;
    private final ListQueuesUseCase list;
    private final FindQueueUseCase find;
    private final PractitionerReader practitioners;

    QueueViewController(CreateQueueUseCase create, RenameQueueUseCase rename, AddPractitionerUseCase addPractitioner,
            RemovePractitionerUseCase removePractitioner, ListQueuesUseCase list, FindQueueUseCase find,
            PractitionerReader practitioners) {
        this.create = create;
        this.rename = rename;
        this.addPractitioner = addPractitioner;
        this.removePractitioner = removePractitioner;
        this.list = list;
        this.find = find;
        this.practitioners = practitioners;
    }

    @GetMapping("/queues")
    String queues(Model model) {
        Map<UUID, String> names = practitionerNames();
        model.addAttribute("queues", list.handle().stream().map(queue -> QueueEntry.of(queue, names)).toList());
        return "queues";
    }

    @PostMapping("/queues")
    String create(@RequestParam String name, @RequestParam(required = false) String room, RedirectAttributes flash) {
        return attempt(flash, "/queues", () -> {
            QueueId id = create.handle(new CreateQueueUseCase.Command(name, room));
            flash.addFlashAttribute("message", "Cola creada.");
            return "/queues/" + id.value();
        });
    }

    @GetMapping("/queues/{id}")
    String queue(@PathVariable UUID id, Model model) {
        QueueView queue;
        try {
            queue = find.handle(new QueueId(id));
        } catch (QueueNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        Map<UUID, String> names = practitionerNames();
        // Only who serves no queue yet may be added: a practitioner serves one at most.
        Set<UUID> serving = list.handle().stream()
                .flatMap(each -> each.practitioners().stream())
                .collect(Collectors.toSet());
        model.addAttribute("queue", QueueEntry.of(queue, names));
        model.addAttribute("available", practitioners.findAll().stream()
                .filter(practitioner -> !serving.contains(practitioner.id()))
                .toList());
        return "queue";
    }

    @PostMapping("/queues/{id}")
    String rename(@PathVariable UUID id, @RequestParam String name, @RequestParam(required = false) String room,
            RedirectAttributes flash) {
        return attempt(flash, "/queues/" + id, () -> {
            rename.handle(new RenameQueueUseCase.Command(new QueueId(id), name, room));
            flash.addFlashAttribute("message", "Cola guardada.");
            return "/queues/" + id;
        });
    }

    @PostMapping("/queues/{id}/practitioners")
    String addPractitioner(@PathVariable UUID id, @RequestParam(required = false) UUID practitionerId,
            RedirectAttributes flash) {
        return attempt(flash, "/queues/" + id, () -> {
            if (practitionerId == null) {
                throw new IllegalArgumentException("A practitioner is needed");
            }
            addPractitioner.handle(new AddPractitionerUseCase.Command(new QueueId(id), practitionerId));
            flash.addFlashAttribute("message", "Profesional agregado a la cola.");
            return "/queues/" + id;
        });
    }

    @PostMapping("/queues/{id}/practitioners/{practitionerId}/remove")
    String removePractitioner(@PathVariable UUID id, @PathVariable UUID practitionerId, RedirectAttributes flash) {
        return attempt(flash, "/queues/" + id, () -> {
            removePractitioner.handle(new RemovePractitionerUseCase.Command(new QueueId(id), practitionerId));
            flash.addFlashAttribute("message", "Profesional quitado de la cola.");
            return "/queues/" + id;
        });
    }

    private Map<UUID, String> practitionerNames() {
        return practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
    }

    /** Runs the command and goes where it says; a refusal comes back to {@code onRefusal} as a message. */
    private static String attempt(RedirectAttributes flash, String onRefusal, Supplier<String> command) {
        try {
            return "redirect:" + command.get();
        } catch (QueueNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", refusal(e));
            return "redirect:" + onRefusal;
        }
    }

    private static String refusal(RuntimeException e) {
        return switch (e) {
            case QueueNameTakenException taken -> "Ya hay otra cola con ese nombre.";
            case PractitionerInAnotherQueueException other -> "Ese profesional ya atiende otra cola: quitalo de ella primero.";
            case QueueRuleViolationException rule -> "No hay nada que cambiar: la cola ya está así.";
            case StreamChangedException changed -> "Alguien cambió la cola mientras tanto. Revisala y probá de nuevo.";
            case UnknownPractitionerException unknown -> "Ese profesional no existe.";
            case IllegalArgumentException invalid -> "Revisá los datos: la cola necesita un nombre de hasta 60 caracteres, y el consultorio, hasta 20.";
            default -> throw e;
        };
    }

    /** A queue as its screens show it, with the names of who serves it. */
    record QueueEntry(UUID id, String name, String room, List<Practitioner> practitioners) {

        static QueueEntry of(QueueView queue, Map<UUID, String> names) {
            return new QueueEntry(queue.id(), queue.name(), queue.room(), queue.practitioners().stream()
                    .map(id -> new Practitioner(id, names.getOrDefault(id, "(profesional desconocido)")))
                    .toList());
        }
    }

    record Practitioner(UUID id, String fullName) {
    }
}
