package com.clinic.schedule.portal.application.port;

import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.portal.domain.Registrant;

/** What the patient module says of an adult who registers on the web, and registers them once confirmed (D-91). */
public interface RegistrantPort {

    /**
     * Whether they gave what a patient may be registered with; nothing is registered.
     *
     * @throws com.clinic.schedule.portal.application.exception.InvalidRegistrationException when not
     */
    void check(Registrant registrant);

    /** Whether the practice already knows the document, as the login would find it. */
    boolean isKnown(Registrant registrant);

    /**
     * Registers them, unverified and marked as registered on the web, in the caller's transaction.
     *
     * @return the new patient's id; nothing when the document is someone's already
     * @throws com.clinic.schedule.portal.application.exception.InvalidRegistrationException when they cannot be one
     */
    Optional<UUID> register(Registrant registrant);
}
