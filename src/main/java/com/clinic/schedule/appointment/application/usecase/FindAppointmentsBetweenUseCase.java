package com.clinic.schedule.appointment.application.usecase;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;

/**
 * The appointments not cancelled from one day to another, of every practitioner: what the reminders ask
 * for, from tomorrow on (D-78). Unlike the agendas, it is not limited to whoever is asking: the reminders'
 * job asks with nobody logged in.
 */
@Service
public class FindAppointmentsBetweenUseCase {

    private final AppointmentRepository appointments;

    public FindAppointmentsBetweenUseCase(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    /** Both days included. */
    @Transactional(readOnly = true)
    public List<Appointment> handle(LocalDate from, LocalDate to) {
        return appointments.findNotCancelledBetween(from, to);
    }
}
