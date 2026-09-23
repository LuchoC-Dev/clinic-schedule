package com.clinic.schedule.appointment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.domain.Appointment;

/**
 * Cancels every appointment whose patient never arrived and whose wait is over, leaving the
 * cancelled state in the database (D-06).
 *
 * <p>The query only narrows the candidates to booked appointments without a check-in that
 * started at least 30 minutes ago — the shortest wait there is. Which of them are due is the
 * appointment's own rule, so a suspended one within its 24 hours is read and left alone. A
 * handful of rows a minute is a price worth paying for keeping the rule in one place (D-31).
 */
@Service
public class CancelOverdueAppointmentsUseCase {

    /** No appointment is due before this; see {@code Appointment.GRACE_PERIOD}. */
    private static final Duration SHORTEST_WAIT = Duration.ofMinutes(30);

    private final AppointmentRepository appointments;
    private final Clock clock;

    public CancelOverdueAppointmentsUseCase(AppointmentRepository appointments, Clock clock) {
        this.appointments = appointments;
        this.clock = clock;
    }

    /** @return the appointments it cancelled */
    @Transactional
    public List<Appointment> handle() {
        LocalDateTime now = LocalDateTime.now(clock);

        List<Appointment> cancelled = appointments.findBookedWithoutCheckInStartingBy(now.minus(SHORTEST_WAIT))
                .stream()
                .filter(appointment -> appointment.isDueForAutoCancellation(now))
                .map(Appointment::cancelled)
                .toList();

        cancelled.forEach(appointments::save);
        return cancelled;
    }
}
