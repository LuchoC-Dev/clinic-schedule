package com.clinic.schedule.arrival.session.command;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.arrival.session.domain.EntryId;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;
import com.clinic.schedule.arrival.session.domain.SessionEvent.PatientArrived;
import com.clinic.schedule.cashdesk.api.VisitCashier;
import com.clinic.schedule.patient.api.PatientWriter;

/**
 * A patient who gives no name comes into a queue (D-60, D-63): registered under an alias and brought
 * in, in one transaction, so a queue that turns out to be closed leaves no nameless patient behind, and
 * neither does a refused charge (D-87): nobody is asked who they are to pay.
 */
@Service
public class AddUnidentifiedPatientUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;
    private final PatientWriter patients;
    private final VisitCashier cashier;

    AddUnidentifiedPatientUseCase(QueueSessionService sessions, SessionCommands commands, PatientWriter patients,
            VisitCashier cashier) {
        this.sessions = sessions;
        this.commands = commands;
        this.patients = patients;
        this.cashier = cashier;
    }

    /** @throws com.clinic.schedule.cashdesk.api.ChargeRefusedException when the visit cannot be charged so */
    @Transactional
    public EntryId handle(Command command) {
        QueueSession session = commands.openSessionOf(command.queueId());
        PatientArrived arrived = sessions.arrive(session, patients.registerUnidentified(), command.emergency(),
                commands.now());
        cashier.visitStarted(arrived.entryId(), arrived.patientId(), command.emergency(), command.amount());
        commands.record(session, arrived);
        return new EntryId(arrived.entryId());
    }

    /** @param amount the pesos charged; null only for an emergency, whose charge is left pending */
    public record Command(UUID queueId, boolean emergency, BigDecimal amount) {
    }
}
