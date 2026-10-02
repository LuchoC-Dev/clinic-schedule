package com.clinic.schedule.security.api;

import java.util.UUID;

/**
 * Which patient is using their web account (D-80): whoever logged in on the patients' side, never a patient
 * named in the request, so no patient can ask for another's.
 */
public interface CurrentPatientReader {

    /**
     * @throws IllegalStateException when whoever is logged in is not a patient, which the patients' routes
     *         never let happen
     */
    UUID patientId();
}
