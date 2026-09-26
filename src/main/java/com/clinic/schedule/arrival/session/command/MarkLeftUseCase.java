package com.clinic.schedule.arrival.session.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;

/** Reception says a waiting patient left without being seen (D-63). */
@Service
public class MarkLeftUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;

    MarkLeftUseCase(QueueSessionService sessions, SessionCommands commands) {
        this.sessions = sessions;
        this.commands = commands;
    }

    @Transactional
    public void handle(Command command) {
        QueueSession session = commands.sessionOf(command.entryId());
        commands.record(session, sessions.leave(session, command.entryId()));
    }

    public record Command(EntryId entryId) {
    }
}
