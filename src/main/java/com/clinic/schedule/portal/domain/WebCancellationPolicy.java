package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;

import com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException;
import com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException.Reason;

/**
 * Until when a patient cancels on the web or from an email's link (D-83): a booked appointment, up to
 * {@code hoursBefore} hours before it starts — 24 by default. Later, they call or come to reception, which may
 * still cancel it, as it always could.
 */
public final class WebCancellationPolicy {

    private WebCancellationPolicy() {
    }

    /** Whether there is still time to cancel it on the web. */
    public static boolean isInTime(LocalDateTime startsAt, LocalDateTime now, int hoursBefore) {
        return !now.isAfter(startsAt.minusHours(hoursBefore));
    }

    /** Whether the patient may cancel it on the web: booked, and in time. */
    public static boolean canCancel(Appointment appointment, LocalDateTime now, int hoursBefore) {
        return appointment.state() == Appointment.State.BOOKED && isInTime(appointment.startsAt(), now, hoursBefore);
    }

    /** @throws WebCancellationRefusedException when it is not booked any more, or there is no time left */
    public static void check(Appointment appointment, LocalDateTime now, int hoursBefore) {
        if (appointment.state() != Appointment.State.BOOKED) {
            throw new WebCancellationRefusedException(Reason.NOT_BOOKED, "The appointment is not booked any more");
        }
        if (!isInTime(appointment.startsAt(), now, hoursBefore)) {
            throw new WebCancellationRefusedException(Reason.TOO_LATE,
                    "An appointment is cancelled on the web up to " + hoursBefore + " hours before it");
        }
    }
}
