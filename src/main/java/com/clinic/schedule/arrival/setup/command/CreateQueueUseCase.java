package com.clinic.schedule.arrival.setup.command;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.setup.domain.QueueEvent.QueueCreated;
import com.clinic.schedule.arrival.setup.domain.QueueId;
import com.clinic.schedule.arrival.setup.domain.QueueSetupService;
import com.clinic.schedule.arrival.setup.projection.QueueProjection;

/**
 * Sets up a new queue, with a name no other queue has and, if it has one, its consulting room; it
 * starts with nobody serving it (D-62). Answers the new queue's id: what it looks like is the
 * reads' business.
 */
@Service
public class CreateQueueUseCase {

    private final QueueSetupService setup;
    private final QueueProjection projection;
    private final QueueCommands commands;

    CreateQueueUseCase(QueueSetupService setup, QueueProjection projection, QueueCommands commands) {
        this.setup = setup;
        this.projection = projection;
        this.commands = commands;
    }

    @Transactional
    public QueueId handle(Command command) {
        QueueCreated created = setup.create(command.name(), command.room(), projection.namesOtherThan(Optional.empty()));
        QueueId id = QueueId.newId();
        commands.record(id, 0, created);
        return id;
    }

    /** @param room null or blank for a queue with no consulting room of its own */
    public record Command(String name, String room) {
    }
}
