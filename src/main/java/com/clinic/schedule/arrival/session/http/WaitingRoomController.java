package com.clinic.schedule.arrival.session.http;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.arrival.session.query.SessionView;
import com.clinic.schedule.arrival.session.query.ViewQueueSessionUseCase;
import com.clinic.schedule.arrival.setup.query.ListQueuesUseCase;
import com.clinic.schedule.arrival.setup.query.QueueView;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * The waiting room's screen in the staff's application (D-66, D-70, D-109): every queue, whether it is open, whom its
 * practitioners are seeing and who waits, in the order they will be called — each by their name alone, the alias of
 * one who gave none. Never their document nor anything else of them: it is a screen anyone in the room sees.
 * Reception and the waiting room's user reach it, by route.
 */
@RestController
class WaitingRoomController {

    private final ListQueuesUseCase queues;
    private final ViewQueueSessionUseCase sessions;
    private final PatientReader patients;

    WaitingRoomController(ListQueuesUseCase queues, ViewQueueSessionUseCase sessions, PatientReader patients) {
        this.queues = queues;
        this.sessions = sessions;
        this.patients = patients;
    }

    @GetMapping("/api/waiting-room")
    List<WaitingRoomResponse> room() {
        List<QueueView> all = queues.handle();
        Map<UUID, Optional<SessionView>> open = all.stream()
                .collect(Collectors.toMap(QueueView::id, queue -> sessions.handle(queue.id())));
        // The names asked once, for every queue at a time (D-33).
        Map<UUID, String> names = patients.findAll(open.values().stream()
                        .flatMap(Optional::stream)
                        .flatMap(session -> Stream.concat(session.called().stream(), session.waiting().stream()))
                        .map(Entry::patientId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, PatientSummary::fullName));
        return all.stream().map(queue -> WaitingRoomResponse.of(queue, open.get(queue.id()), names)).toList();
    }
}
