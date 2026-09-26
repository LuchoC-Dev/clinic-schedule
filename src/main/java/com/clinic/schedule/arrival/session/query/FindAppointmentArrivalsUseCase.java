package com.clinic.schedule.arrival.session.query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * When the patient of each appointment arrived (D-65): the first time they came into a queue with
 * it, or the check-in recorded on the appointment before the queues took the arrival over. The
 * appointment module asks it, and nothing about an appointment says so any more.
 */
@Service
public class FindAppointmentArrivalsUseCase {

    private final SessionViewReader sessions;

    FindAppointmentArrivalsUseCase(SessionViewReader sessions) {
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public Map<UUID, LocalDateTime> handle(Collection<UUID> appointmentIds) {
        return sessions.appointmentArrivals(appointmentIds);
    }
}
