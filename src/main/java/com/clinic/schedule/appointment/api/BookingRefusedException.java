package com.clinic.schedule.appointment.api;

/**
 * An appointment that could not be booked, and why, in terms a caller can tell a patient (D-82), without
 * knowing this module's own exceptions (D-24).
 */
public class BookingRefusedException extends RuntimeException {

    public enum Reason {
        /** Someone else took the practitioner's time. */
        TIME_TAKEN,
        /** The patient has another appointment at that time. */
        PATIENT_BUSY,
        /** Not a time, a reason or a practitioner that can be booked. */
        NOT_BOOKABLE
    }

    private final Reason reason;

    public BookingRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
