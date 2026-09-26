package com.clinic.schedule.arrival.session.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;
import com.clinic.schedule.arrival.session.projection.SessionProjection;

/**
 * Closes the queues whose day is over, unless reception kept them open (D-63). The projection
 * narrows the candidates to open sessions that close by themselves; whether each is due is the
 * session's own rule. Recorded as done by the system.
 */
@Service
public class CloseDueSessionsUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;
    private final SessionProjection projection;

    CloseDueSessionsUseCase(QueueSessionService sessions, SessionCommands commands, SessionProjection projection) {
        this.sessions = sessions;
        this.commands = commands;
        this.projection = projection;
    }

    /** @return how many it closed */
    @Transactional
    public int handle() {
        int closed = 0;
        for (var id : projection.openClosingByThemselves()) {
            QueueSession session = commands.find(id);
            var due = sessions.closeIfDue(session, commands.now());
            commands.recordAsSystem(session, due);
            closed += due.size();
        }
        return closed;
    }
}
