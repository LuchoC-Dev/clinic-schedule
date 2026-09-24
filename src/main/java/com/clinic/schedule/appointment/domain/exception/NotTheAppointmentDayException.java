package com.clinic.schedule.appointment.domain.exception;

import java.time.LocalDate;

/** An arrival or a late-arrival notice for an appointment that is not today (D-38). */
public class NotTheAppointmentDayException extends BookingRuleViolationException {

    public NotTheAppointmentDayException(String action, LocalDate day) {
        super("An appointment can only be %s on its own day, %s".formatted(action, day));
    }
}
