package com.clinic.schedule.appointment.application.exception;

import com.clinic.schedule.appointment.domain.PractitionerId;

/** No practitioner with that id. Maps to 400, since the request names someone who does not exist. */
public class UnknownPractitionerException extends RuntimeException {

    public UnknownPractitionerException(PractitionerId practitionerId) {
        super("There is no practitioner with id " + practitionerId.value());
    }
}
