package com.clinic.schedule.arrival.session.command;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.api.AppointmentReader;
import com.clinic.schedule.appointment.api.AppointmentSummary;
import com.clinic.schedule.arrival.session.command.AppointmentArrivalRefusedException.Reason;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;
import com.clinic.schedule.arrival.session.domain.SessionEvent.AppointmentArrived;
import com.clinic.schedule.cashdesk.api.Means;
import com.clinic.schedule.cashdesk.api.VisitCashier;

/**
 * A patient with an appointment today arrives (D-64): they come into the queue of the appointment's
 * practitioner, kept for them, and are placed by the later of the appointment's time and now. The
 * appointment has to be booked and today, and its practitioner has to serve an open queue.
 *
 * <p>The visit is charged in the same step and transaction, or the patient does not come in (D-87).
 */
@Service
public class AppointmentArrivesUseCase {

    private final QueueSessionService sessions;
    private final SessionCommands commands;
    private final AppointmentReader appointments;
    private final VisitCashier cashier;

    AppointmentArrivesUseCase(QueueSessionService sessions, SessionCommands commands, AppointmentReader appointments,
            VisitCashier cashier) {
        this.sessions = sessions;
        this.commands = commands;
        this.appointments = appointments;
        this.cashier = cashier;
    }

    /**
     * @return the queue they came into
     * @throws AppointmentArrivalRefusedException when that appointment brings nobody in today
     * @throws com.clinic.schedule.cashdesk.api.ChargeRefusedException when the visit cannot be charged so
     */
    @Transactional
    public UUID handle(Command command) {
        AppointmentSummary appointment = appointments.findBooked(command.appointmentId())
                .orElseThrow(() -> new AppointmentArrivalRefusedException(Reason.NOT_BOOKED));
        if (!appointment.startsAt().toLocalDate().equals(commands.now().toLocalDate())) {
            throw new AppointmentArrivalRefusedException(Reason.NOT_TODAY);
        }
        UUID queue = commands.queueOf(appointment.practitionerId())
                .orElseThrow(() -> new AppointmentArrivalRefusedException(Reason.NO_QUEUE));
        QueueSession session = commands.openSessionOf(queue);
        AppointmentArrived arrived = sessions.arriveWithAppointment(session, appointment.patientId(), appointment.id(),
                appointment.practitionerId(), appointment.startsAt(), commands.now());
        cashier.visitStarted(arrived.entryId(), appointment.patientId(), false, command.amount(), command.means());
        commands.record(session, arrived);
        return queue;
    }

    /**
     * @param amount the pesos charged: a patient with an appointment does not come in as an emergency
     * @param means how they were paid; null for cash
     */
    public record Command(UUID appointmentId, BigDecimal amount, Means means) {
    }
}
