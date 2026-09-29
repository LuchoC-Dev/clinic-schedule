package com.clinic.schedule.patient.api;

import java.util.UUID;

/**
 * Registers patients for other modules. Kept apart from {@link PatientReader} because it writes,
 * and the name says so (D-18).
 *
 * <p>Its callers join the registration to their own transaction: booking a new patient registers
 * them and books them at once, so a refused booking leaves no patient behind (D-37, D-75), and the
 * arrival queue registers a patient who gives no name as they come in (D-63).
 */
public interface PatientWriter {

    /**
     * @return the new patient's id
     * @throws PatientRegistrationRefusedException when the name is missing, the document is
     *         incomplete, malformed or already someone else's, or the phone or the email cannot be
     *         right; nothing is registered then
     */
    UUID register(NewPatient patient);

    /**
     * A patient who gives no name, under a new alias such as NN-4821 (D-60). Its caller is the
     * arrival queue, where such a patient comes in (D-63).
     *
     * @return the new patient's id
     */
    UUID registerUnidentified();
}
