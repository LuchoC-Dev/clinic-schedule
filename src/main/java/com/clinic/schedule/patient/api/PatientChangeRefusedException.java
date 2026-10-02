package com.clinic.schedule.patient.api;

/**
 * A change a patient asked for their own data could not be made: a phone, an email or an insurance that
 * cannot be right, an owner without a name or a relation, or agreeing to be reminded on a channel that is
 * not there. Nothing is changed then. Its own type, so a caller need not know this module's exceptions
 * (D-24).
 */
public class PatientChangeRefusedException extends RuntimeException {

    public PatientChangeRefusedException(String message) {
        super(message);
    }
}
