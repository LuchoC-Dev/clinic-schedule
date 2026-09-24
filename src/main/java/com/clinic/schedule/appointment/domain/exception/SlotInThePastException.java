package com.clinic.schedule.appointment.domain.exception;

import com.clinic.schedule.appointment.domain.TimeSlot;

/** The requested slot starts before now: an appointment is booked or moved from now on (D-38). */
public class SlotInThePastException extends BookingRuleViolationException {

    public SlotInThePastException(TimeSlot slot) {
        super("The slot starting at %s is in the past".formatted(slot.startsAt()));
    }
}
