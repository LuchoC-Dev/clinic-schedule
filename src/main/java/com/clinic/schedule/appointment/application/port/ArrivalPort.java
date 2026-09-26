package com.clinic.schedule.appointment.application.port;

import com.clinic.schedule.appointment.domain.AppointmentId;

/**
 * The patients' arrivals, as the use cases need them (D-64). A {@code Port}: it is another module,
 * not storage (D-18). Answered through {@code api/AppointmentArrivals}, which that module
 * implements.
 */
public interface ArrivalPort {

    /** Tells the arrivals an appointment was cancelled. */
    void appointmentCancelled(AppointmentId id);
}
