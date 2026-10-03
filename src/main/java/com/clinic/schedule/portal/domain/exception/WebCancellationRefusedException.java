package com.clinic.schedule.portal.domain.exception;

/**
 * An appointment that may not be cancelled on the web or from an email's link, and why (D-83). Maps to 409,
 * or to 404 for a link that leads to nothing.
 */
public class WebCancellationRefusedException extends RuntimeException {

    public enum Reason {
        /** It is cancelled or absent already. */
        NOT_BOOKED,
        /** There is less time left than the web cancels with. */
        TOO_LATE,
        /** A link that leads to no appointment booked at the time it was sent for. */
        GONE
    }

    private final Reason reason;

    public WebCancellationRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
