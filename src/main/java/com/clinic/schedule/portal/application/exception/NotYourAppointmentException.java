package com.clinic.schedule.portal.application.exception;

import java.util.UUID;

/**
 * A patient asked for an appointment that is not theirs, or none at all. Maps to 403: asking for what one may
 * not see is refused, never answered as if it were not there (D-50).
 */
public class NotYourAppointmentException extends RuntimeException {

    public NotYourAppointmentException(UUID appointmentId) {
        super("Appointment " + appointmentId + " is not the patient's");
    }
}
