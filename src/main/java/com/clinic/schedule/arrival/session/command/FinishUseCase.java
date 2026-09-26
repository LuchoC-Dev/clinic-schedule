package com.clinic.schedule.arrival.session.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;

/** "Terminar": the practitioner is done with the patient they called, who was seen (D-63). */
@Service
public class FinishUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;

    FinishUseCase(QueueSessionService sessions, SessionCommands commands) {
        this.sessions = sessions;
        this.commands = commands;
    }

    @Transactional
    public void handle() {
        QueueSession session = practitionersSession();
        commands.record(session, sessions.finish(session, commands.currentPractitioner()));
    }

    private QueueSession practitionersSession() {
        return commands.openSessionOf(commands.queueServedBy(commands.currentPractitioner()));
    }
}
