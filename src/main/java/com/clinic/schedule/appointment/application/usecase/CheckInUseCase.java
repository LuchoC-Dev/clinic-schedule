package com.clinic.schedule.appointment.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.exception.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;

/**
 * The patient stops at reception and their arrival is confirmed. From then on the appointment
 * is never marked absent (D-61).
 *
 * <p>The time of the check-in is the practice's clock, not something the caller sends, so it
 * cannot be backdated.
 */
@Service
public class CheckInUseCase {

    private final AppointmentRepository appointments;
    private final Clock clock;

    public CheckInUseCase(AppointmentRepository appointments, Clock clock) {
        this.appointments = appointments;
        this.clock = clock;
    }

    @Transactional
    public Appointment handle(AppointmentId id) {
        Appointment appointment = appointments.findById(id).orElseThrow(() -> new AppointmentNotFoundException(id));

        Appointment checkedIn = appointment.checkedIn(LocalDateTime.now(clock));
        appointments.save(checkedIn);
        return checkedIn;
    }
}
