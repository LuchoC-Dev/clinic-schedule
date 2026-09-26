package com.clinic.schedule.arrival.session.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientArrived;
import com.clinic.schedule.patient.api.PatientWriter;

/**
 * A patient who gives no name comes into a queue (D-60, D-63): registered under an alias and brought
 * in, in one transaction, so a queue that turns out to be closed leaves no nameless patient behind.
 */
@Service
public class AddUnidentifiedPatientUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;
    private final PatientWriter patients;

    AddUnidentifiedPatientUseCase(QueueSessionService sessions, SessionCommands commands, PatientWriter patients) {
        this.sessions = sessions;
        this.commands = commands;
        this.patients = patients;
    }

    @Transactional
    public EntryId handle(Command command) {
        QueueSession session = commands.openSessionOf(command.queueId());
        PatientArrived arrived = sessions.arrive(session, patients.registerUnidentified(), command.emergency(),
                commands.now());
        commands.record(session, arrived);
        return new EntryId(arrived.entryId());
    }

    public record Command(UUID queueId, boolean emergency) {
    }
}
