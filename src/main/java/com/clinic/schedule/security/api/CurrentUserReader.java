package com.clinic.schedule.security.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Who is using the application, as much as other modules need to know. The only public way
 * into the security module (D-24).
 *
 * <p>Other modules do not ask for roles: they ask the one question that changes what they do —
 * whether the user stands for a practitioner, and so may only see that practitioner's agenda.
 */
public interface CurrentUserReader {

    /**
     * The practitioner the current user stands for, or empty when the user is reception and may
     * see every practitioner.
     */
    Optional<UUID> practitionerId();
}
