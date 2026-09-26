package com.clinic.schedule.arrival.setup.query;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every queue as it is set up, by name, with who serves each (D-62). */
@Service
public class ListQueuesUseCase {

    private final QueueViewReader queues;

    ListQueuesUseCase(QueueViewReader queues) {
        this.queues = queues;
    }

    @Transactional(readOnly = true)
    public List<QueueView> handle() {
        return queues.findAll();
    }
}
