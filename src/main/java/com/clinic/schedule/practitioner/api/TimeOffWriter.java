package com.clinic.schedule.practitioner.api;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records a practitioner's time off. Kept apart from {@link PractitionerReader} because it
 * writes, and the name says so (D-18).
 *
 * <p>It is called by the appointment module, which owns what follows from an absence — the
 * cancellation of the affected appointments — inside the same transaction, so the absence and
 * its consequences are stored together or not at all (D-32).
 */
public interface TimeOffWriter {

    /** @return false when there is no practitioner with that id, and nothing was recorded */
    boolean recordTimeOff(UUID practitionerId, LocalDateTime startsAt, LocalDateTime endsAt);
}
