package com.clinic.schedule.arrival.session.command;

/**
 * A patient cannot come into a queue with that appointment (D-64). Maps to 409. It says why, so a
 * screen can put it in its own words.
 */
public class AppointmentArrivalRefusedException extends RuntimeException {

    /** Why the appointment does not bring its patient in. */
    public enum Reason {
        NOT_BOOKED("No booked appointment with that id: it does not exist, is cancelled, or its patient was absent"),
        NOT_TODAY("A patient comes in with an appointment on its own day"),
        NO_QUEUE("The appointment's practitioner serves no queue");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    AppointmentArrivalRefusedException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
