package com.clinic.schedule.appointment.api;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Books appointments for other modules: a patient booking their own on the web (D-82). Kept apart from
 * {@link AppointmentReader} because it writes, and the name says so (D-18).
 */
public interface AppointmentWriter {

    /**
     * Books it as reception would — the reason's duration, the practitioner's hours, no overlap with their
     * agenda nor with the patient's —, marked as booked on the web, with no note. It joins the caller's
     * transaction. Which reasons, days and times a patient may book on the web is the caller's to decide.
     *
     * @return the new appointment's id
     * @throws BookingRefusedException when it cannot be booked; nothing is booked then
     */
    UUID bookOnWeb(UUID patientId, UUID practitionerId, UUID reasonId, LocalDateTime startsAt);
}
