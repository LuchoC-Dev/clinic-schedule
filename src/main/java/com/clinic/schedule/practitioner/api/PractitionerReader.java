package com.clinic.schedule.practitioner.api;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask about a practitioner. The only public way into this module
 * besides its HTTP endpoints (D-24): everything else in the package is package-private.
 *
 * <p>Read-only, hence the name. It answers with plain values and its own DTOs, so no other
 * module sees this one's entities (D-05).
 */
public interface PractitionerReader {

    /**
     * The reason with that id, among that practitioner's reasons. Empty when either the
     * practitioner or the reason does not exist, or the reason belongs to someone else.
     */
    Optional<ReasonDetails> findReason(UUID practitionerId, UUID reasonId);

    /**
     * Whether the practitioner works for the whole span, within a single stretch of their
     * usual week. False for an unknown practitioner, who works no hours at all.
     */
    boolean worksDuring(UUID practitionerId, LocalDateTime startsAt, LocalDateTime endsAt);
}
