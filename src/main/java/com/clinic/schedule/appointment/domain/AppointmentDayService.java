package com.clinic.schedule.appointment.domain;

import java.time.LocalDateTime;
import java.util.Optional;

import com.clinic.schedule.appointment.domain.exception.BookingRuleViolationException;

/**
 * The rules of an appointment's day that depend on whether its patient arrived (D-65). The arrival
 * is not the appointment's any more — the queues keep it — so it is given here as context, and the
 * appointment's own operations are reached through this service alone (D-17):
 *
 * <ul>
 *   <li>a late-arrival notice makes no sense once the patient arrived;</li>
 *   <li>a patient who arrived is never absent.</li>
 * </ul>
 *
 * <p>Moving an appointment whose patient arrived is refused by {@link BookingService#reschedule},
 * which is the way in for a move.
 */
public class AppointmentDayService {

    /** @param arrivedAt when the patient arrived, if they did */
    public Appointment lateArrivalNotice(Appointment appointment, LocalDateTime now, Optional<LocalDateTime> arrivedAt) {
        requireNotArrived(arrivedAt, "given more time");
        return appointment.withLateArrivalNotice(now);
    }

    /** Whether the patient is absent now: they did not arrive, and the wait is over. */
    public boolean isDueForAbsence(Appointment appointment, LocalDateTime now, Optional<LocalDateTime> arrivedAt) {
        return arrivedAt.isEmpty() && appointment.isOverdue(now);
    }

    /** @throws BookingRuleViolationException when the patient arrived, or may still come */
    public Appointment markAbsent(Appointment appointment, LocalDateTime now, Optional<LocalDateTime> arrivedAt) {
        requireNotArrived(arrivedAt, "marked absent");
        return appointment.markedAbsent(now);
    }

    static void requireNotArrived(Optional<LocalDateTime> arrivedAt, String action) {
        if (arrivedAt.isPresent()) {
            throw new BookingRuleViolationException("An appointment whose patient already arrived cannot be " + action);
        }
    }
}
