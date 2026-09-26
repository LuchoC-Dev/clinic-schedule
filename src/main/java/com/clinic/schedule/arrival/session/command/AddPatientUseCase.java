package com.clinic.schedule.arrival.session.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientArrived;
import com.clinic.schedule.patient.api.PatientReader;

/**
 * Reception brings a patient into an open queue, as an emergency or not (D-63). Only reception
 * does: a practitioner only calls. A patient may wait in more than one queue, but in each once.
 */
@Service
public class AddPatientUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;
    private final PatientReader patients;

    AddPatientUseCase(QueueSessionService sessions, SessionCommands commands, PatientReader patients) {
        this.sessions = sessions;
        this.commands = commands;
        this.patients = patients;
    }

    /** @throws UnknownPatientException when no patient has that id */
    @Transactional
    public EntryId handle(Command command) {
        QueueSession session = commands.openSessionOf(command.queueId());
        if (command.patientId() == null || !patients.exists(command.patientId())) {
            throw new UnknownPatientException(command.patientId());
        }
        PatientArrived arrived = sessions.arrive(session, command.patientId(), command.emergency(), commands.now());
        commands.record(session, arrived);
        return new EntryId(arrived.entryId());
    }

    public record Command(UUID queueId, UUID patientId, boolean emergency) {
    }
}
