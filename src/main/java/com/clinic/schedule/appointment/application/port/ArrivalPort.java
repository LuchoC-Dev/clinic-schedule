package com.clinic.schedule.appointment.application.port;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.clinic.schedule.appointment.domain.AppointmentId;

/**
 * The patients' arrivals, as the use cases need them (D-64, D-65). A {@code Port}: it is another
 * module, not storage (D-18). Answered through {@code api/AppointmentArrivals}, which that module
 * implements. The appointment keeps no arrival of its own: whoever asks whether a patient came asks
 * here.
 */
public interface ArrivalPort {

    /** Tells the arrivals an appointment was cancelled. */
    void appointmentCancelled(AppointmentId id);

    /** When the patient of each appointment arrived; the ones who did not are left out. */
    Map<AppointmentId, LocalDateTime> arrivalsOf(Collection<AppointmentId> ids);

    default Optional<LocalDateTime> arrivalOf(AppointmentId id) {
        return Optional.ofNullable(arrivalsOf(List.of(id)).get(id));
    }
}
