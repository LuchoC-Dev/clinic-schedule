package com.clinic.schedule.portal.domain.exception;

/**
 * A web account that may not be opened for that patient (D-79), and why. Maps to 409: the request is well
 * formed, and refused for the patient's state.
 */
public class AccountRefusedException extends RuntimeException {

    public enum Reason {
        IDENTITY_NOT_VERIFIED,
        NO_BIRTH_DATE,
        UNDER_THIRTEEN,
        ALREADY_OPENED
    }

    private final Reason reason;

    public AccountRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
