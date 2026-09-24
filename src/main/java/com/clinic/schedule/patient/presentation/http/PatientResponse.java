package com.clinic.schedule.patient.presentation.http;

import java.util.UUID;

import com.clinic.schedule.patient.entity.DocumentType;
import com.clinic.schedule.patient.entity.IdentityDocument;
import com.clinic.schedule.patient.entity.Patient;

/**
 * A patient as the API returns it. The document comes back as a nested object, mirroring
 * that it is one value with two parts, and as null for a patient without one.
 */
record PatientResponse(UUID id, String fullName, Document document) {

    record Document(DocumentType type, String number) {
    }

    static PatientResponse of(Patient patient) {
        IdentityDocument document = patient.getDocument();
        return new PatientResponse(
                patient.getId(),
                patient.getFullName(),
                document == null ? null : new Document(document.type(), document.number()));
    }
}
