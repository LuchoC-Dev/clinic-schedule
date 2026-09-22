package com.clinic.schedule.appointment.domain;

import java.util.UUID;

/**
 * Identity of an appointment, assigned when it is created rather than by the database.
 *
 * <p>Two reasons, in order of how much they matter here:
 *
 * <ul>
 *   <li><b>Idempotency.</b> Reception double-clicks and two requests arrive carrying the
 *       same id: the second one does not create a second appointment.</li>
 *   <li>The aggregate has identity from birth, so there is no {@code null} id to reason
 *       about and no round trip to the database to learn it.</li>
 * </ul>
 *
 * <p>A random UUID (v4) is not ordered, which fragments the index. At this scale — three
 * practitioners, a few thousand rows a year — that does not matter, and it avoids either a
 * dependency or hand-written v7 generation.
 */
public record AppointmentId(UUID value) {

    public AppointmentId {
        if (value == null) {
            throw new IllegalArgumentException("An appointment needs an id");
        }
    }

    public static AppointmentId newId() {
        return new AppointmentId(UUID.randomUUID());
    }

    public static AppointmentId of(String value) {
        return new AppointmentId(UUID.fromString(value));
    }
}
