package com.clinic.schedule.patient.api;

import java.util.UUID;

/**
 * What other modules may ask about patients. The only public way into this module besides
 * its HTTP endpoints (D-24): everything else in the package is package-private.
 *
 * <p>It asks for no more than booking needs today. When the patient file arrives and wants
 * names and documents, this is where they are added (D-14).
 */
public interface PatientReader {

    boolean exists(UUID patientId);
}
