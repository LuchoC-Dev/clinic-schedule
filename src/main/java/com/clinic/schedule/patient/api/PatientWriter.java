package com.clinic.schedule.patient.api;

import java.util.UUID;

/**
 * Registers patients for other modules. Kept apart from {@link PatientReader} because it writes,
 * and the name says so (D-18).
 *
 * <p>Its caller is the booking screen, which registers a new patient and books them in one go
 * (D-37).
 */
public interface PatientWriter {

    /**
     * @param documentType one of {@link PatientReader#documentTypes()}, or null together with a
     *        null or blank number for a patient without a document
     * @return the new patient's id
     * @throws PatientRegistrationRefusedException when the name is missing, or the document is
     *         incomplete, malformed or already someone else's; nothing is registered then
     */
    UUID register(String fullName, String documentType, String documentNumber);
}
