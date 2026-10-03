package com.clinic.schedule.appointment.api;

/**
 * An appointment that cannot be cancelled: there is none, or it is not booked any more — cancelled or absent
 * meanwhile (D-83). Its own type, so a caller need not know this module's exceptions (D-24).
 */
public class CancellationRefusedException extends RuntimeException {

    public CancellationRefusedException(String message) {
        super(message);
    }
}
