package com.clinic.schedule.appointment.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;

/**
 * Marks absent every appointment whose patient never arrived and whose wait is over (D-61). It
 * replaces the automatic cancellation of v1: the appointment stays as the record that the patient
 * did not come.
 *
 * <p>The query only narrows the candidates to booked appointments without a check-in that
 * started at least 30 minutes ago — the shortest wait there is. Which of them are due is the
 * appointment's own rule, so one whose patient warned they would be late is read and left alone
 * until its day is over. A handful of rows a minute is a price worth paying for keeping the rule
 * in one place (D-31).
 */
@Service
public class MarkAbsentAppointmentsUseCase {

    /** No appointment is due before this; see {@code Appointment.GRACE_PERIOD}. */
    private static final Duration SHORTEST_WAIT = Duration.ofMinutes(30);

    private final AppointmentRepository appointments;
    private final Clock clock;

    public MarkAbsentAppointmentsUseCase(AppointmentRepository appointments, Clock clock) {
        this.appointments = appointments;
        this.clock = clock;
    }

    /** @return the appointments it marked absent */
    @Transactional
    public List<Appointment> handle() {
        LocalDateTime now = LocalDateTime.now(clock);

        List<Appointment> absent = appointments.findBookedWithoutCheckInStartingBy(now.minus(SHORTEST_WAIT))
                .stream()
                .filter(appointment -> appointment.isDueForAbsence(now))
                .map(appointment -> appointment.markedAbsent(now))
                .toList();

        absent.forEach(appointments::save);
        return absent;
    }
}
