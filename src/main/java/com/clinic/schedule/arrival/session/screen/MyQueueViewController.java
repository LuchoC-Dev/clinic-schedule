package com.clinic.schedule.arrival.session.screen;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.clinic.schedule.arrival.session.command.CallNextUseCase;
import com.clinic.schedule.arrival.session.command.DidNotComeInUseCase;
import com.clinic.schedule.arrival.session.command.FinishUseCase;
import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.arrival.session.query.ViewMyQueueUseCase;
import com.clinic.schedule.arrival.session.query.ViewMyQueueUseCase.MyQueue;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The practitioner's screen of their queue (D-63): who waits, in the order they will be called,
 * each with their record to read; "Llamar siguiente"; and, with a patient called, "Empezar
 * consulta", "Terminar" and "No ingresó". The practitioner never chooses whom to call.
 */
@Controller
class MyQueueViewController {

    private final ViewMyQueueUseCase view;
    private final CallNextUseCase callNext;
    private final FinishUseCase finish;
    private final DidNotComeInUseCase didNotComeIn;
    private final PatientReader patients;
    private final PractitionerReader practitioners;

    MyQueueViewController(ViewMyQueueUseCase view, CallNextUseCase callNext, FinishUseCase finish,
            DidNotComeInUseCase didNotComeIn, PatientReader patients, PractitionerReader practitioners) {
        this.view = view;
        this.callNext = callNext;
        this.finish = finish;
        this.didNotComeIn = didNotComeIn;
        this.patients = patients;
        this.practitioners = practitioners;
    }

    @GetMapping("/my-queue")
    String myQueue(Model model) {
        // The header offers a practitioner their own screens only (D-34).
        model.addAttribute("readOnly", true);
        Optional<MyQueue> mine = view.handle();
        model.addAttribute("serves", mine.isPresent());
        mine.ifPresent(queue -> {
            model.addAttribute("queue", queue.queue());
            model.addAttribute("open", queue.session().isPresent());
            queue.session().ifPresent(session -> {
                List<Entry> shown = Stream.concat(session.waiting().stream(), queue.called().stream()).toList();
                Map<UUID, PatientSummary> names = patients.findAll(shown.stream().map(Entry::patientId).distinct().toList())
                        .stream().collect(Collectors.toMap(PatientSummary::id, Function.identity()));
                Map<UUID, String> practitionerNames = practitioners.findAll().stream()
                        .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
                model.addAttribute("waiting", session.waiting().stream()
                        .map(entry -> QueueLine.of(entry, names, practitionerNames)).toList());
                model.addAttribute("called", queue.called()
                        .map(entry -> QueueLine.of(entry, names, practitionerNames)).orElse(null));
            });
        });
        return "my-queue";
    }

    @PostMapping("/my-queue/call-next")
    String callNext(RedirectAttributes flash) {
        return attempt(flash, callNext::handle);
    }

    @PostMapping("/my-queue/finish")
    String finish(RedirectAttributes flash) {
        return attempt(flash, finish::handle);
    }

    @PostMapping("/my-queue/did-not-come-in")
    String didNotComeIn(RedirectAttributes flash) {
        return attempt(flash, didNotComeIn::handle);
    }

    private static String attempt(RedirectAttributes flash, Runnable command) {
        try {
            command.run();
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", ScreenRefusals.of(e));
        }
        return "redirect:/my-queue";
    }
}
