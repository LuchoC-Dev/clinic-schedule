package com.clinic.schedule.practitioner.entity;

/**
 * Two stretches of the same day overlap or meet. Maps to 400: the schedule that was sent
 * does not describe a week, so there is nothing to retry except fixing it.
 */
public class InvalidWeeklyScheduleException extends RuntimeException {

    InvalidWeeklyScheduleException(WorkingHours first, WorkingHours second) {
        super(("Working hours %s and %s overlap or meet. Stretches that meet are a single "
                + "stretch; a break is a gap between two.").formatted(first, second));
    }
}
