package com.clinic.schedule.appointment.application;

import com.clinic.schedule.appointment.domain.AppointmentId;

/** No appointment with that id. Belongs here and not to the domain: looking things up is
 * the application's job. */
public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException(AppointmentId id) {
        super("There is no appointment with id " + id.value());
    }
}
