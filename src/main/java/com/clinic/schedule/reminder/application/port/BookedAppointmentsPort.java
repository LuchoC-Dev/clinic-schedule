package com.clinic.schedule.reminder.application.port;

import java.time.LocalDate;
import java.util.List;

import com.clinic.schedule.reminder.domain.BookedAppointment;

/** The appointments still booked, asked of the appointment module (D-78). A {@code Port}: it is another module. */
public interface BookedAppointmentsPort {

    /** Both days included, of every practitioner; asked from tomorrow on, where none is absent yet. */
    List<BookedAppointment> between(LocalDate from, LocalDate to);
}
