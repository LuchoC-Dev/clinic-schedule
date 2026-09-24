package com.clinic.schedule.patient.presentation.http;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.clinic.schedule.patient.entity.DocumentType;
import com.clinic.schedule.patient.entity.IdentityDocument;

/**
 * The body for registering or updating a patient.
 *
 * <p>The document is optional, but it comes as a pair: both parts or neither. Sending only
 * one is answered with a 400, not silently ignored — a number without a type identifies
 * nobody.
 */
record PatientRequest(
        @NotBlank(message = "the full name is required")
        @Size(max = 150, message = "the full name is at most 150 characters")
        String fullName,

        DocumentType documentType,

        @Size(max = 20, message = "the document number is at most 20 characters")
        String documentNumber) {

    IdentityDocument toDocument() {
        return IdentityDocument.ofNullable(documentType, documentNumber);
    }
}
