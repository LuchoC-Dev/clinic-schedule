package com.clinic.schedule.appointment.api;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Books, moves and cancels appointments for other modules: a patient booking their own on the web (D-82),
 * moving it there (D-93), and cancelling it there or from an email's link (D-83). Kept apart from
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

    /**
     * Moves it to another start time as reception would — its booked length, the practitioner's hours, no
     * overlap with their agenda nor with the patient's —, and marks it as moved on the web. It joins the
     * caller's transaction. Which days and times a patient may move it to on the web is the caller's to decide.
     *
     * @throws BookingRefusedException when it cannot be moved there, or is not booked any more; nothing changes
     *         then
     */
    void moveOnWeb(UUID appointmentId, LocalDateTime startsAt);

    /**
     * Cancels it as the patient logged in asked from their web account: recorded as made by the account,
     * asked by the patient. Whether they may is the caller's to decide. It joins the caller's transaction.
     *
     * @throws CancellationRefusedException when it is not booked any more
     */
    void cancelOnWeb(UUID appointmentId);

    /**
     * Cancels it as asked from an email's link, with nobody logged in: recorded as made from the link, asked
     * by the patient.
     *
     * @throws CancellationRefusedException when it is not booked any more
     */
    void cancelFromEmailLink(UUID appointmentId);
}
