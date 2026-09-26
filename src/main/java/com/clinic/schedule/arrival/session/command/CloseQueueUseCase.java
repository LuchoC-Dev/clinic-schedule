package com.clinic.schedule.arrival.session.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;

/** Reception closes a queue: whoever was still waiting was not seen, and does not come back if it reopens (D-63). */
@Service
public class CloseQueueUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;

    CloseQueueUseCase(QueueSessionService sessions, SessionCommands commands) {
        this.sessions = sessions;
        this.commands = commands;
    }

    @Transactional
    public void handle(Command command) {
        QueueSession session = commands.openSessionOf(command.queueId());
        commands.record(session, sessions.close(session, commands.now()));
    }

    public record Command(UUID queueId) {
    }
}
