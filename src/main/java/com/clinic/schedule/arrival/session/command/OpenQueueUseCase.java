package com.clinic.schedule.arrival.session.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.QueueSessionService;
import com.clinic.schedule.arrival.session.domain.SessionId;

/** Reception opens a queue: a new session starts, empty. A queue has one open session at a time (D-63). */
@Service
public class OpenQueueUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;

    OpenQueueUseCase(QueueSessionService sessions, SessionCommands commands) {
        this.sessions = sessions;
        this.commands = commands;
    }

    @Transactional
    public SessionId handle(Command command) {
        commands.requireQueue(command.queueId());
        return commands.open(sessions.open(command.queueId(), commands.openSessionIdOf(command.queueId()), commands.now()));
    }

    public record Command(UUID queueId) {
    }
}
