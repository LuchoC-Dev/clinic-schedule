package com.clinic.schedule.arrival.session.query;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.arrival.setup.query.ListQueuesUseCase;
import com.clinic.schedule.arrival.setup.query.QueueView;
import com.clinic.schedule.security.api.CurrentUserReader;

/**
 * The queue of the practitioner using the application (D-63): which one they serve, its open
 * session if any, and the patient they have called. Empty when they serve no queue.
 */
@Service
public class ViewMyQueueUseCase {

    private final ListQueuesUseCase queues;
    private final SessionViewReader sessions;
    private final CurrentUserReader currentUser;

    ViewMyQueueUseCase(ListQueuesUseCase queues, SessionViewReader sessions, CurrentUserReader currentUser) {
        this.queues = queues;
        this.sessions = sessions;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public Optional<MyQueue> handle() {
        UUID practitioner = currentUser.practitionerId()
                .orElseThrow(() -> new IllegalStateException("Only a practitioner has a queue of their own"));
        return queues.handle().stream()
                .filter(queue -> queue.practitioners().contains(practitioner))
                .findFirst()
                .map(queue -> {
                    Optional<SessionView> session = sessions.openSessionOf(queue.id());
                    Optional<Entry> called = session.flatMap(open -> open.called().stream()
                            .filter(entry -> practitioner.equals(entry.withPractitioner()))
                            .findFirst());
                    return new MyQueue(queue, session, called);
                });
    }

    /**
     * @param session empty while the queue is closed
     * @param called the patient this practitioner called and is seeing
     */
    public record MyQueue(QueueView queue, Optional<SessionView> session, Optional<Entry> called) {
    }
}
