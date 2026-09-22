package com.clinic.schedule.appointment.domain;

/**
 * A booking rule says no. Subclasses name the rule, so a caller can tell the cases apart
 * without matching on message text.
 */
public class BookingRuleViolationException extends RuntimeException {

    public BookingRuleViolationException(String message) {
        super(message);
    }
}
