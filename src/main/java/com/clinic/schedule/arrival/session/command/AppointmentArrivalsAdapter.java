package com.clinic.schedule.arrival.session.command;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.api.AppointmentArrivals;
import com.clinic.schedule.arrival.session.domain.QueueSession;
import com.clinic.schedule.arrival.session.domain.QueueSessionService;
import com.clinic.schedule.arrival.session.projection.SessionProjection;

/**
 * Answers what the appointment module tells the arrivals (D-64): an appointment cancelled while
 * its patient waits in an open queue leaves them in their place, kept for nobody. Called by
 * another module rather than over HTTP, and inside its transaction, so the cancellation and what it
 * does to the queue are kept together.
 */
@Component
class AppointmentArrivalsAdapter implements AppointmentArrivals {

    private final QueueSessionService sessions;
    private final SessionCommands commands;
    private final SessionProjection projection;

    AppointmentArrivalsAdapter(QueueSessionService sessions, SessionCommands commands, SessionProjection projection) {
        this.sessions = sessions;
        this.commands = commands;
        this.projection = projection;
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
