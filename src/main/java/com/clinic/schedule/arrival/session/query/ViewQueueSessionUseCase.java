package com.clinic.schedule.arrival.session.query;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.setup.domain.QueueId;
import com.clinic.schedule.arrival.setup.query.FindQueueUseCase;

/** A queue's open session, with who waits in order, who was called and who is done; empty while it is closed (D-63). */
@Service
public class ViewQueueSessionUseCase {

    private final SessionViewReader sessions;
    private final FindQueueUseCase queues;

    ViewQueueSessionUseCase(SessionViewReader sessions, FindQueueUseCase queues) {
        this.sessions = sessions;
        this.queues = queues;
    }

    /** @throws com.clinic.schedule.arrival.setup.QueueNotFoundException when no queue has that id */
    @Transactional(readOnly = true)
    public Optional<SessionView> handle(UUID queueId) {
        queues.handle(new QueueId(queueId));
        return sessions.openSessionOf(queueId);
    }
}
