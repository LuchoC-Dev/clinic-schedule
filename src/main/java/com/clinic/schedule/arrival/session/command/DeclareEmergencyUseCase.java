package com.clinic.schedule.arrival.session.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;

/** Reception makes a waiting patient an emergency; they are placed among the emergencies from now (D-63). */
@Service
public class DeclareEmergencyUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;

    DeclareEmergencyUseCase(QueueSessionService sessions, SessionCommands commands) {
        this.sessions = sessions;
        this.commands = commands;
    }

    @Transactional
    public void handle(Command command) {
        QueueSession session = commands.sessionOf(command.entryId());
        commands.record(session, sessions.declareEmergency(session, command.entryId(), commands.now()));
    }

    public record Command(EntryId entryId) {
    }
}
