package com.clinic.schedule.appointment.application.exception;

import java.time.LocalDate;

/**
 * An absence on a day already gone: there is nothing left to cancel, so recording it can only
 * be a mistyped date (D-38). A kind of bad request, answered 400 like the rest; its own type
 * lets a screen say so in its words (D-46).
 */
public class TimeOffInThePastException extends IllegalArgumentException {

    public TimeOffInThePastException(LocalDate day) {
        super("Time off cannot be recorded for a day already gone: " + day);
    }
}
