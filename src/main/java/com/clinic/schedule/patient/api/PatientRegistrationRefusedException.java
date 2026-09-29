package com.clinic.schedule.patient.api;

/**
 * A patient could not be registered. The reason is part of the contract, so a caller can tell
 * reception what to fix without knowing this module's own exceptions (D-24).
 */
public class PatientRegistrationRefusedException extends RuntimeException {

    public enum Reason {
        /** The document already belongs to another patient. */
        DUPLICATE_DOCUMENT,
        /** A document type without a number, or a number without a type. */
        INCOMPLETE_DOCUMENT,
        /** No name, an unknown document type, or a number that type does not allow. */
        INVALID_DATA,
        /** A phone or an email that cannot be right, or its owner without a name or a relation (D-73). */
        INVALID_DETAILS
    }

    private final Reason reason;

    public PatientRegistrationRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
