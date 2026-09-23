package com.clinic.schedule.appointment.application;

import com.clinic.schedule.appointment.domain.PatientId;

/**
 * No patient is registered with that id. Maps to 400, since the request names someone who
 * cannot be booked; reception registers the patient first.
 */
public class UnknownPatientException extends RuntimeException {

    public UnknownPatientException(PatientId patientId) {
        super("There is no registered patient with id " + patientId.value());
    }
}
