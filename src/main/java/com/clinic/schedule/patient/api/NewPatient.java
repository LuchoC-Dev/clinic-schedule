package com.clinic.schedule.patient.api;

/**
 * A patient to register, as another module hands them over: in plain values, so the caller need not
 * know this module's types (D-24). Everything but the name is optional: blank or null is not given.
 *
 * <p>The phone and the email each say whose they are (D-73): with no owner they are the patient's own;
 * otherwise the owner is a name and a relation, one of {@link PatientReader#relations()}.
 *
 * @param documentType one of {@link PatientReader#documentTypes()}, or blank together with the number
 */
public record NewPatient(String fullName, String documentType, String documentNumber,
        String phoneNumber, String phoneOwnerRelation, String phoneOwnerName,
        String emailAddress, String emailOwnerRelation, String emailOwnerName) {
}
