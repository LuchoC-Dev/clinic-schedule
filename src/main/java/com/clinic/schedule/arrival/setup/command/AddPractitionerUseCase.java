package com.clinic.schedule.arrival.setup.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.setup.domain.Queue;
import com.clinic.schedule.arrival.setup.domain.QueueId;
import com.clinic.schedule.arrival.setup.domain.QueueSetupService;
import com.clinic.schedule.arrival.setup.projection.QueueProjection;
import com.clinic.schedule.practitioner.api.PractitionerReader;

/**
 * A practitioner starts serving a queue: one who exists, and serves no other queue (D-62). Serving
 * a queue will let them read the records of the patients waiting in it, so every change is kept in
 * the queue's events, with who made it and when.
 */
@Service
public class AddPractitionerUseCase {

    private final QueueSetupService setup;
    private final QueueProjection projection;
    private final QueueCommands commands;
    private final PractitionerReader practitioners;

    AddPractitionerUseCase(QueueSetupService setup, QueueProjection projection, QueueCommands commands,
            PractitionerReader practitioners) {
        this.setup = setup;
        this.projection = projection;
        this.commands = commands;
        this.practitioners = practitioners;
    }

    /** @throws UnknownPractitionerException when no practitioner has that id */
    @Transactional
    public void handle(Command command) {
        Queue queue = commands.find(command.queueId());
        if (practitioners.findAll().stream().noneMatch(found -> found.id().equals(command.practitionerId()))) {
            throw new UnknownPractitionerException(command.practitionerId());
        }
        commands.record(queue.id(), queue.version(), setup.join(queue, command.practitionerId(),
                projection.queueServedBy(command.practitionerId())));
    }

    public record Command(QueueId queueId, UUID practitionerId) {
    }
}
