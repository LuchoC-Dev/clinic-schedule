package com.clinic.schedule.appointment.domain;

/** How an appointment came to be cancelled (D-76). */
public enum CancellationOrigin {

    /** Reception cancelled it. */
    BY_HAND,

    /** A practitioner's time off cancelled it, as it was recorded (D-32). */
    TIME_OFF
}
