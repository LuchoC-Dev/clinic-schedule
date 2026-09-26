package com.clinic.schedule.arrival.session.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;

/** Reception lifts the reservation of a waiting patient: whoever serves the queue may call them (D-64). */
@Service
public class LiftReservationUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;

    LiftReservationUseCase(QueueSessionService sessions, SessionCommands commands) {
        this.sessions = sessions;
        this.commands = commands;
    }

    @Transactional
    public void handle(Command command) {
        QueueSession session = commands.sessionOf(command.entryId());
        commands.record(session, sessions.liftReservation(session, command.entryId()));
    }

    public record Command(EntryId entryId) {
    }
}
