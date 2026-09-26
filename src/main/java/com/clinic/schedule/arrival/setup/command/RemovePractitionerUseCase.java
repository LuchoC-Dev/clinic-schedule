package com.clinic.schedule.arrival.setup.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.setup.domain.Queue;
import com.clinic.schedule.arrival.setup.domain.QueueId;
import com.clinic.schedule.arrival.setup.domain.QueueSetupService;

/**
 * A practitioner stops serving a queue. The queue stays, even with nobody left to serve it: it is
 * set up once and kept (D-62).
 */
@Service
public class RemovePractitionerUseCase {

    private final QueueSetupService setup;
    private final QueueCommands commands;

    RemovePractitionerUseCase(QueueSetupService setup, QueueCommands commands) {
        this.setup = setup;
        this.commands = commands;
    }

    @Transactional
    public void handle(Command command) {
        Queue queue = commands.find(command.queueId());
        commands.record(queue.id(), queue.version(), setup.leave(queue, command.practitionerId()));
    }

    public record Command(QueueId queueId, UUID practitionerId) {
    }
}
