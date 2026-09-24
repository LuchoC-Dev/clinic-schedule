package com.clinic.schedule.appointment.application.port;

import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * What the agenda needs from the practitioner module: how long a reason takes with a
 * practitioner, whether they work during a slot (D-08), and recording their time off so the
 * affected appointments can be handled in the same transaction (D-32).
 *
 * <p>A {@code Port} and not a {@code Reader}: it is not storage, it is another module
 * (D-18). It is declared here, in this module's own terms — {@link PractitionerId},
 * {@link TimeSlot} — so the use cases do not depend on the shape of the practitioner
 * module's api. An adapter translates between the two.
 */
public interface PractitionerSchedulePort {

    /** Empty when the practitioner has no reason with that id. */
    Optional<BookableReason> findReason(PractitionerId practitionerId, UUID reasonId);

    /** Whether the whole slot falls inside the practitioner's working hours and outside any time off. */
    boolean worksDuring(PractitionerId practitionerId, TimeSlot slot);

    /**
     * Records that the practitioner is absent for the span.
     *
     * @return false when there is no such practitioner, and nothing was recorded
     */
    boolean recordTimeOff(PractitionerId practitionerId, TimeSlot span);

    /** The part of a reason booking cares about: what to call it, and how long to reserve. */
    record BookableReason(String name, int durationMinutes) {
    }
}
