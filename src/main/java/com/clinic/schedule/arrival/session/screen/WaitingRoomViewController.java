package com.clinic.schedule.arrival.session.screen;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.clinic.schedule.arrival.session.query.SessionView;
import com.clinic.schedule.arrival.session.query.ViewQueueSessionUseCase;
import com.clinic.schedule.arrival.setup.query.ListQueuesUseCase;

/**
 * The waiting-room screen (D-66): every queue, whether it is open, and how many wait and are being
 * seen. It names no patient: it is a screen anyone in the room can see. It shows itself again as
 * the queues change, and will grow later. The screen of the room opens it with a user of its own,
 * who may see it and nothing else; reception may open it too (D-70).
 */
@Controller
class WaitingRoomViewController {

    private final ListQueuesUseCase queues;
    private final ViewQueueSessionUseCase sessions;

    WaitingRoomViewController(ListQueuesUseCase queues, ViewQueueSessionUseCase sessions) {
        this.queues = queues;
        this.sessions = sessions;
    }

    @GetMapping("/waiting-room")
    String waitingRoom(Model model) {
        model.addAttribute("queues", queues.handle().stream()
                .map(queue -> {
                    Optional<SessionView> session = sessions.handle(queue.id());
                    return new RoomQueue(queue.id(), queue.name(), queue.room(), session.isPresent(),
                            session.map(open -> open.waiting().size()).orElse(0),
                            session.map(open -> open.called().size()).orElse(0));
                })
                .toList());
        return "waiting-room";
    }

    /** A queue as the waiting room shows it: no patient in it by name. */
    record RoomQueue(UUID id, String name, String room, boolean open, int waiting, int beingSeen) {
    }
}
