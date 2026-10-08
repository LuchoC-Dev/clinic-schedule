package com.clinic.schedule.arrival.session.command;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientArrived;
import com.clinic.schedule.cashdesk.api.Means;
import com.clinic.schedule.cashdesk.api.VisitCashier;
import com.clinic.schedule.patient.api.PatientReader;

/**
 * Reception brings a patient into an open queue, as an emergency or not (D-63). Only reception
 * does: a practitioner only calls. A patient may wait in more than one queue, but in each once.
 *
 * <p>The visit is charged in the same step and transaction, or the patient does not come in; an emergency
 * comes in uncharged, its charge pending (D-87).
 */
@Service
public class AddPatientUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;
    private final PatientReader patients;
    private final VisitCashier cashier;

    AddPatientUseCase(QueueSessionService sessions, SessionCommands commands, PatientReader patients,
            VisitCashier cashier) {
        this.sessions = sessions;
        this.commands = commands;
        this.patients = patients;
        this.cashier = cashier;
    }

    /**
     * @throws UnknownPatientException when no patient has that id
     * @throws com.clinic.schedule.cashdesk.api.ChargeRefusedException when the visit cannot be charged so
     */
    @Transactional
    public EntryId handle(Command command) {
        QueueSession session = commands.openSessionOf(command.queueId());
        if (command.patientId() == null || !patients.exists(command.patientId())) {
            throw new UnknownPatientException(command.patientId());
        }
        PatientArrived arrived = sessions.arrive(session, command.patientId(), command.emergency(), commands.now());
        cashier.visitStarted(arrived.entryId(), command.patientId(), command.emergency(), command.amount(),
                command.means());
        commands.record(session, arrived);
        return new EntryId(arrived.entryId());
    }

    /**
     * @param amount the pesos charged; null only for an emergency, whose charge is left pending
     * @param means how they were paid; null for cash
     */
    public record Command(UUID queueId, UUID patientId, boolean emergency, BigDecimal amount, Means means) {
    }
}
