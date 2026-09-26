package com.clinic.schedule.arrival.session.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;

/** "No ingresó": the called patient did not come in; they are passed over once, or out after their last call (D-63). */
@Service
public class DidNotComeInUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;

    DidNotComeInUseCase(QueueSessionService sessions, SessionCommands commands) {
        this.sessions = sessions;
        this.commands = commands;
    }

    @Transactional
    public void handle() {
        QueueSession session = commands.openSessionOf(commands.queueServedBy(commands.currentPractitioner()));
        commands.record(session, sessions.didNotComeIn(session, commands.currentPractitioner()));
    }
}
