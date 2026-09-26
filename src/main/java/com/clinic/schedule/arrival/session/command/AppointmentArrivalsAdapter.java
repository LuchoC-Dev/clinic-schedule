package com.clinic.schedule.arrival.session.command;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.api.AppointmentArrivals;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;
import com.clinic.schedule.arrival.session.projection.SessionProjection;
import com.clinic.schedule.arrival.session.query.FindAppointmentArrivalsUseCase;

/**
 * Answers what the appointment module tells and asks the arrivals (D-64, D-65): an appointment
 * cancelled while its patient waits in an open queue leaves them in their place, kept for nobody;
 * and when the patient of each appointment arrived, which the appointment no longer keeps. Called by
 * another module rather than over HTTP, and inside its transaction.
 */
@Component
class AppointmentArrivalsAdapter implements AppointmentArrivals {

    private final QueueSessionService sessions;
    private final SessionCommands commands;
    private final SessionProjection projection;
    private final FindAppointmentArrivalsUseCase arrivals;

    AppointmentArrivalsAdapter(QueueSessionService sessions, SessionCommands commands, SessionProjection projection,
            FindAppointmentArrivalsUseCase arrivals) {
        this.sessions = sessions;
        this.commands = commands;
        this.projection = projection;
        this.arrivals = arrivals;
    }

    @Override
    public Map<UUID, LocalDateTime> arrivalsOf(Collection<UUID> appointmentIds) {
        return arrivals.handle(appointmentIds);
    }

    @Override
    @Transactional
    public void appointmentCancelled(UUID appointmentId) {
        projection.openSessionWith(appointmentId).ifPresent(id -> {
            QueueSession session = commands.find(id);
            commands.recordAll(session, sessions.appointmentCancelled(session, appointmentId));
        });
    }
}
