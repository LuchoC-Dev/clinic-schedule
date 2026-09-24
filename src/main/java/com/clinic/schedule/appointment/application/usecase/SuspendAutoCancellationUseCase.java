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
 * The patient called to say they will be late: the appointment waits 24 hours instead of 30
 * minutes before being cancelled automatically (D-06). Only on the day of the appointment (D-38).
 */
@Service
public class SuspendAutoCancellationUseCase {

    private final AppointmentRepository appointments;
    private final Clock clock;

    public SuspendAutoCancellationUseCase(AppointmentRepository appointments, Clock clock) {
        this.appointments = appointments;
        this.clock = clock;
    }

    @Transactional
    public Appointment handle(AppointmentId id) {
        Appointment appointment = appointments.findById(id).orElseThrow(() -> new AppointmentNotFoundException(id));

        Appointment suspended = appointment.withAutoCancellationSuspended(LocalDateTime.now(clock));
        appointments.save(suspended);
        return suspended;
    }
}
