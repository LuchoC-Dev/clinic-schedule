package com.clinic.schedule.appointment.application;

import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * What booking needs to know about a practitioner: how long a reason takes with them, and
 * whether they work during a slot (D-08).
 *
 * <p>A {@code Port} and not a {@code Reader}: it is not storage, it is another module
 * (D-18). It is declared here, in this module's own terms — {@link PractitionerId},
 * {@link TimeSlot} — so the use cases do not depend on the shape of the practitioner
 * module's api. An adapter translates between the two.
 */
public interface PractitionerSchedulePort {

    /** Empty when the practitioner has no reason with that id. */
    Optional<BookableReason> findReason(PractitionerId practitionerId, UUID reasonId);

    /** Whether the whole slot falls inside the practitioner's working hours. */
    boolean worksDuring(PractitionerId practitionerId, TimeSlot slot);

    /** The part of a reason booking cares about: what to call it, and how long to reserve. */
    record BookableReason(String name, int durationMinutes) {
    }
}
