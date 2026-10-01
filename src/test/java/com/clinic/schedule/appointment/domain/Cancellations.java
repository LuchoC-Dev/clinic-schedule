package com.clinic.schedule.appointment.domain;

import java.time.LocalDateTime;

/** A cancellation for tests about something else than what is recorded of it (D-76). */
public final class Cancellations {

    private Cancellations() {
    }

    /** Reception cancelled it on the tests' day, as the patient asked. */
    public static Cancellation byHand() {
        return Cancellation.byHand(LocalDateTime.of(2026, 3, 10, 0, 0), "recepcion", CancellationRequester.PATIENT,
                null);
    }
}
