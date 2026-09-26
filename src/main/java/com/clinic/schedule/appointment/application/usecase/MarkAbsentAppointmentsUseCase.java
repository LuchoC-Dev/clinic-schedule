package com.clinic.schedule.appointment.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentDayService;
import com.clinic.schedule.appointment.application.port.ArrivalPort;
import com.clinic.schedule.appointment.domain.Appointment;

/**
 * Marks absent every appointment whose patient never arrived and whose wait is over (D-61). It
 * replaces the automatic cancellation of v1: the appointment stays as the record that the patient
 * did not come.
 *
 * <p>The query only narrows the candidates to booked appointments that started at least 30
 * minutes ago — the shortest wait there is —, and the arrivals say whose patient came (D-65). Which of them are due is the
 * appointment's own rule, so one whose patient warned they would be late is read and left alone
 * until its day is over. A handful of rows a minute is a price worth paying for keeping the rule
 * in one place (D-31).
 */
@Service
public class MarkAbsentAppointmentsUseCase {

    /** No appointment is due before this; see {@code Appointment.GRACE_PERIOD}. */
    private static final Duration SHORTEST_WAIT = Duration.ofMinutes(30);

    private final AppointmentRepository appointments;
    private final ArrivalPort arrivals;
    private final AppointmentDayService day;
    private final Clock clock;

    public MarkAbsentAppointmentsUseCase(AppointmentRepository appointments, ArrivalPort arrivals,
            AppointmentDayService day, Clock clock) {
        this.appointments = appointments;
        this.arrivals = arrivals;
        this.day = day;
        this.clock = clock;
    }

    /** @return the appointments it marked absent */
    @Transactional
    public List<Appointment> handle() {
        LocalDateTime now = LocalDateTime.now(clock);

        List<Appointment> candidates = appointments.findBookedStartingBy(now.minus(SHORTEST_WAIT));
        Map<AppointmentId, LocalDateTime> arrived = arrivals.arrivalsOf(candidates.stream().map(Appointment::id).toList());
        List<Appointment> absent = candidates.stream()
                .filter(appointment -> day.isDueForAbsence(appointment, now,
                        Optional.ofNullable(arrived.get(appointment.id()))))
                .map(appointment -> day.markAbsent(appointment, now, Optional.empty()))
                .toList();

        absent.forEach(appointments::save);
        return absent;
    }
}
