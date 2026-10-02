package com.clinic.schedule.reminder.adapter.out.external;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.api.AppointmentReader;
import com.clinic.schedule.reminder.application.port.BookedAppointmentsPort;
import com.clinic.schedule.reminder.domain.BookedAppointment;

/**
 * Answers {@link BookedAppointmentsPort} by asking the appointment module through its api (D-05). The
 * appointments not cancelled from tomorrow on are the booked ones: one is absent only once it has started
 * (D-61), so none of them is.
 */
@Component
class BookedAppointmentsAdapter implements BookedAppointmentsPort {

    private final AppointmentReader appointments;

    BookedAppointmentsAdapter(AppointmentReader appointments) {
        this.appointments = appointments;
    }

    @Override
    public List<BookedAppointment> between(LocalDate from, LocalDate to) {
        return appointments.findNotCancelledBetween(from, to).stream()
                .map(found -> new BookedAppointment(found.id(), found.patientId(), found.practitionerId(),
                        found.startsAt()))
                .toList();
    }
}
