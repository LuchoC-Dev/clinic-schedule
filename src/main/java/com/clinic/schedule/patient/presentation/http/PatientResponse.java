package com.clinic.schedule.patient.presentation.http;

import java.util.UUID;

import com.clinic.schedule.patient.entity.DocumentType;
import com.clinic.schedule.patient.entity.IdentityDocument;
import com.clinic.schedule.patient.entity.IdentityStatus;
import com.clinic.schedule.patient.entity.Patient;

/**
 * A patient as the API returns it. The document comes back as a nested object, mirroring
 * that it is one value with two parts, and as null for a patient without one. {@code identity}
 * says how far the practice knows who they are, and {@code alias} is the one they went by when
 * they gave no name, null for everyone else (D-60).
 */
record PatientResponse(UUID id, String fullName, Document document, IdentityStatus identity, String alias) {

    record Document(DocumentType type, String number) {
    }

    static PatientResponse of(Patient patient) {
        IdentityDocument document = patient.getDocument();
        return new PatientResponse(
                patient.getId(),
                patient.getFullName(),
                document == null ? null : new Document(document.type(), document.number()),
                patient.getIdentityStatus(),
                patient.getAlias());
    }
}
