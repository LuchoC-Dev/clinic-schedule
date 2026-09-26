package com.clinic.schedule.arrival.session.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;

/** The practitioner calls the next patient of the queue they serve, as the order says; they never choose (D-63). */
@Service
public class CallNextUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;

    CallNextUseCase(QueueSessionService sessions, SessionCommands commands) {
        this.sessions = sessions;
        this.commands = commands;
    }

    @Transactional
    public void handle() {
        UUID practitioner = commands.currentPractitioner();
        UUID queue = commands.queueServedBy(practitioner);
        QueueSession session = commands.openSessionOf(queue);
        commands.record(session, sessions.callNext(session, practitioner, commands.servedBy(queue), commands.now()));
    }
}
