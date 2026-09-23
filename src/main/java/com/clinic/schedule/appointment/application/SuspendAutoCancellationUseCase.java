package com.clinic.schedule.appointment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;

/**
 * The patient called to say they will be late: the appointment waits 24 hours instead of 30
 * minutes before being cancelled automatically (D-06).
 */
@Service
public class SuspendAutoCancellationUseCase {

    private final AppointmentRepository appointments;

    public SuspendAutoCancellationUseCase(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Transactional
    public Appointment handle(AppointmentId id) {
        Appointment appointment = appointments.findById(id).orElseThrow(() -> new AppointmentNotFoundException(id));

        Appointment suspended = appointment.withAutoCancellationSuspended();
        appointments.save(suspended);
        return suspended;
    }
}
