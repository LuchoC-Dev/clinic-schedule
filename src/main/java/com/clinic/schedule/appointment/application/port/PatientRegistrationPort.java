package com.clinic.schedule.appointment.application.port;

import com.clinic.schedule.appointment.domain.PatientId;

/**
 * Registers a patient booking brings in for the first time (D-37, D-75). Registering joins the
 * booking's transaction, so a booking that is refused takes the patient back with it.
 *
 * <p>A {@code Port}: it is another module, not storage (D-18). Declared in this module's terms and
 * answered by an adapter, like {@link PatientRegistryPort} (D-29).
 */
public interface PatientRegistrationPort {

    /**
     * @return the new patient
     * @throws RuntimeException the patient module's refusal, which the caller hands on as it is
     */
    PatientId register(NewPatient patient);

    /**
     * Who to register, as reception typed them: a name, maybe a document, maybe a phone and an email,
     * each with whose it is — blank or null for what was not given.
     */
    record NewPatient(String fullName, String documentType, String documentNumber,
            String phoneNumber, String phoneOwnerRelation, String phoneOwnerName,
            String emailAddress, String emailOwnerRelation, String emailOwnerName) {
    }
}
