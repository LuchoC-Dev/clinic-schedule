package com.clinic.schedule.appointment.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.exception.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;

/** One appointment, by id. The screen that moves an appointment needs to show it first. */
@Service
public class FindAppointmentUseCase {

    private final AppointmentRepository appointments;

    public FindAppointmentUseCase(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Transactional(readOnly = true)
    public Appointment handle(AppointmentId id) {
        return appointments.findById(id).orElseThrow(() -> new AppointmentNotFoundException(id));
    }
}
