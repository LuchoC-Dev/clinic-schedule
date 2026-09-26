package com.clinic.schedule.arrival.setup.command;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.setup.domain.Queue;
import com.clinic.schedule.arrival.setup.domain.QueueId;
import com.clinic.schedule.arrival.setup.domain.QueueSetupService;
import com.clinic.schedule.arrival.setup.projection.QueueProjection;

/** Changes a queue's name or consulting room; the name stays nobody else's (D-62). */
@Service
public class RenameQueueUseCase {

    private final QueueSetupService setup;
    private final QueueProjection projection;
    private final QueueCommands commands;

    RenameQueueUseCase(QueueSetupService setup, QueueProjection projection, QueueCommands commands) {
        this.setup = setup;
        this.projection = projection;
        this.commands = commands;
    }

    @Transactional
    public void handle(Command command) {
        Queue queue = commands.find(command.id());
        commands.record(queue.id(), queue.version(), setup.rename(queue, command.name(), command.room(),
                projection.namesOtherThan(Optional.of(queue.id()))));
    }

    /** @param room null or blank for a queue with no consulting room of its own */
    public record Command(QueueId id, String name, String room) {
    }
}
