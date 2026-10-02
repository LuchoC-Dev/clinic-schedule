package com.clinic.schedule.portal.api;

import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask about patients' web accounts. The only way into this module besides its
 * endpoints and screens (D-24), and read-only, hence the name (D-18).
 */
public interface PatientAccountReader {

    /**
     * Whether the patient has a web account, in any state: theirs logs in with their document, so the patient
     * module keeps their identity verified (D-79).
     */
    boolean hasAccount(UUID patientId);

    /** The patient's account, if it is in use, for the security module to log them in (D-80). */
    Optional<LoginAccount> findActiveForLogin(UUID patientId);
}
