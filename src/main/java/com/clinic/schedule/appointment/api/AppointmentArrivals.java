package com.clinic.schedule.appointment.api;

import java.util.UUID;

/**
 * What this module tells whoever keeps the patients' arrivals — the arrival queues (D-64). It is
 * declared here and implemented there, so the queues depend on appointments and never the other
 * way round, as D-48 asked: this module knows it tells someone, not whom.
 */
public interface AppointmentArrivals {

    /** The appointment was cancelled: a patient who came in with it is kept for nobody any more. */
    void appointmentCancelled(UUID appointmentId);
}
