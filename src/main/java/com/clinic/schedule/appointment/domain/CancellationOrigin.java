package com.clinic.schedule.appointment.domain;

/** How an appointment came to be cancelled (D-76). */
public enum CancellationOrigin {

    /** Reception cancelled it. */
    BY_HAND,

    /** A practitioner's time off cancelled it, as it was recorded (D-32). */
    TIME_OFF,

    /** The patient cancelled it from their web account (D-83). */
    ONLINE,

    /** Someone cancelled it from the link in its reminder or its confirmation, without logging in (D-83). */
    EMAIL_LINK
}
