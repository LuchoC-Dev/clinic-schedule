package com.clinic.schedule.arrival.session.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;

/**
 * Reception gives a waiting patient to another practitioner of the queue — when theirs left it, most
 * of all — and they keep their place (D-64). The appointment does not change.
 */
@Service
public class ReassignUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;

    ReassignUseCase(QueueSessionService sessions, SessionCommands commands) {
        this.sessions = sessions;
        this.commands = commands;
    }

    @Transactional
    public void handle(Command command) {
        QueueSession session = commands.sessionOf(command.entryId());
        commands.record(session, sessions.reassign(session, command.entryId(), command.practitionerId(),
                commands.servedBy(session.queueId())));
    }

    public record Command(EntryId entryId, UUID practitionerId) {
    }
}
