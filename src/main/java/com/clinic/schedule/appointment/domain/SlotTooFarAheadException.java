package com.clinic.schedule.appointment.domain;

/** The requested slot starts more than a year ahead, most likely a mistyped year (D-38). */
public class SlotTooFarAheadException extends BookingRuleViolationException {

    public SlotTooFarAheadException(TimeSlot slot) {
        super("The slot starting at %s is more than a year ahead".formatted(slot.startsAt()));
    }
}
