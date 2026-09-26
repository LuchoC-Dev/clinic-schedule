package com.clinic.schedule.arrival.setup.query;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.setup.QueueNotFoundException;
import com.clinic.schedule.arrival.setup.domain.QueueId;

/** One queue as it is set up (D-62). */
@Service
public class FindQueueUseCase {

    private final QueueViewReader queues;

    FindQueueUseCase(QueueViewReader queues) {
        this.queues = queues;
    }

    /** @throws QueueNotFoundException when no queue has that id */
    @Transactional(readOnly = true)
    public QueueView handle(QueueId id) {
        return queues.find(id.value()).orElseThrow(() -> new QueueNotFoundException(id));
    }
}
