package com.clinic.schedule.portal.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;

import com.clinic.schedule.portal.domain.exception.WebMoveRefusedException;
import com.clinic.schedule.portal.domain.exception.WebMoveRefusedException.Reason;

/**
 * Where a patient may move one of their appointments on the web (D-93), on top of what any move must be — the
 * appointment module's rules, the same as reception's: the rules of cancelling and of booking together.
 *
 * <ul>
 *   <li>a booked appointment, up to {@code hoursBefore} hours before it, as cancelling it ({@link
 *       WebCancellationPolicy});</li>
 *   <li>to a day from tomorrow to {@code daysAhead} days on, at one of the times the agenda offers to move it to
 *       that day, as booking ({@link WebBookingPolicy}), and not the time it already has.</li>
 * </ul>
 *
 * <p>The practitioner and the reason are the appointment's own, and no limit of appointments ahead is counted:
 * it is the same appointment, as many times as the patient wants. It is handed what it needs and reads nothing
 * itself.
 */
public final class WebMovePolicy {

    private WebMovePolicy() {
    }

    /** Whether the patient may still move it on the web: booked, and in time — when they may cancel it. */
    public static boolean canMove(Appointment appointment, LocalDateTime now, int hoursBefore) {
        return WebCancellationPolicy.canCancel(appointment, now, hoursBefore);
    }

    /** @throws WebMoveRefusedException when it is not booked any more, or there is no time left */
    public static void requireMovable(Appointment appointment, LocalDateTime now, int hoursBefore) {
        if (appointment.state() != Appointment.State.BOOKED) {
            throw new WebMoveRefusedException(Reason.NOT_BOOKED, "The appointment is not booked any more");
        }
        if (!WebCancellationPolicy.isInTime(appointment.startsAt(), now, hoursBefore)) {
            throw new WebMoveRefusedException(Reason.TOO_LATE,
                    "An appointment is moved on the web up to " + hoursBefore + " hours before it");
        }
    }

    /** @throws WebMoveRefusedException when the day is not one the web moves an appointment to */
    public static void requireDay(LocalDate day, LocalDate today, int daysAhead) {
        if (!WebBookingPolicy.isBookableDay(day, today, daysAhead)) {
            throw new WebMoveRefusedException(Reason.DAY_OUT_OF_RANGE,
                    "The web moves an appointment from tomorrow to " + daysAhead + " days ahead");
        }
    }

    /**
     * @param offered the times the agenda offers to move it to on the new day
     * @throws WebMoveRefusedException when the patient may not move it there on the web
     */
    public static void check(Appointment appointment, LocalDateTime startsAt, Collection<LocalTime> offered,
            LocalDateTime now, int daysAhead, int hoursBefore) {
        requireMovable(appointment, now, hoursBefore);
        requireDay(startsAt.toLocalDate(), now.toLocalDate(), daysAhead);
        if (startsAt.equals(appointment.startsAt())) {
            throw new WebMoveRefusedException(Reason.SAME_TIME, "The appointment is already at that time");
        }
        if (!offered.contains(startsAt.toLocalTime())) {
            throw new WebMoveRefusedException(Reason.NOT_AN_OFFERED_TIME, "That time is not offered that day");
        }
    }
}
