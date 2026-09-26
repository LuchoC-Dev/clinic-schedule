package com.clinic.schedule.arrival.session.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;

/** Reception keeps a queue open past the end of its day, for as long as this session lasts (D-63). */
@Service
public class TurnOffAutoCloseUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;

    TurnOffAutoCloseUseCase(QueueSessionService sessions, SessionCommands commands) {
        this.sessions = sessions;
        this.commands = commands;
    }

    @Transactional
    public void handle(Command command) {
        QueueSession session = commands.openSessionOf(command.queueId());
        commands.record(session, sessions.turnOffAutoClose(session));
    }

    public record Command(UUID queueId) {
    }
}
