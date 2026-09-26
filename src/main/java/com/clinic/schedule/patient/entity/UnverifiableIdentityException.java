package com.clinic.schedule.patient.entity;

/**
 * An identity said to be verified that cannot be: there is no document to have seen, or the
 * patient still goes by their alias. Maps to 400, since the request itself does not make sense.
 */
public class UnverifiableIdentityException extends RuntimeException {

    UnverifiableIdentityException(String message) {
        super(message);
    }
}
