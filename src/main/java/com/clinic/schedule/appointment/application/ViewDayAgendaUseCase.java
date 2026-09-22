package com.clinic.schedule.appointment.application;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.PractitionerId;

/**
 * A practitioner's day, in order. The view reception works with all day.
 *
 * <p>Cancelled appointments are left out: this answers "what is happening today", not the
 * history, which is what the patient file will show.
 *
 * <p>The practitioner comes in as a parameter with nobody checking who is asking, until
 * there is authentication (D-04).
 */
@Service
public class ViewDayAgendaUseCase {

    private final AppointmentRepository appointments;

    public ViewDayAgendaUseCase(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Transactional(readOnly = true)
    public List<Appointment> handle(PractitionerId practitionerId, LocalDate date) {
        return appointments.findBookedForPractitionerOn(practitionerId, date).stream()
                .sorted(Comparator.comparing(appointment -> appointment.slot().startsAt()))
                .toList();
    }
}
