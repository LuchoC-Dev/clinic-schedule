package com.clinic.schedule.appointment.api;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * What this module tells, and asks, whoever keeps the patients' arrivals — the arrival queues
 * (D-64, D-65). It is
 * declared here and implemented there, so the queues depend on appointments and never the other
 * way round, as D-48 asked: this module knows it tells someone, not whom.
 */
public interface AppointmentArrivals {

    /** The appointment was cancelled: a patient who came in with it is kept for nobody any more. */
    void appointmentCancelled(UUID appointmentId);

    /** When the patient of each of those appointments arrived; the ones who did not are left out. */
    Map<UUID, LocalDateTime> arrivalsOf(Collection<UUID> appointmentIds);
}
