package com.clinic.schedule.patient.presentation.http;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.clinic.schedule.patient.entity.DocumentType;
import com.clinic.schedule.patient.entity.IdentityDocument;
import com.clinic.schedule.patient.entity.PatientDetails;

/**
 * The body for registering a patient: who they are, as in {@link PatientRequest}, and what else is
 * known of them already, as in {@link PatientDetailsRequest} — all of it optional (D-73).
 */
record RegisterPatientRequest(
        @NotBlank(message = "the full name is required")
        @Size(max = 150, message = "the full name is at most 150 characters")
        String fullName,

        DocumentType documentType,

        @Size(max = 20, message = "the document number is at most 20 characters")
        String documentNumber,

        Boolean documentSeen,

        LocalDate birthDate,

        PatientDetailsRequest.PhoneRequest phone,

        PatientDetailsRequest.EmailRequest email) {

    IdentityDocument toDocument() {
        return IdentityDocument.ofNullable(documentType, documentNumber);
    }

    boolean seen() {
        return Boolean.TRUE.equals(documentSeen);
    }

    PatientDetails toDetails() {
        return new PatientDetailsRequest(birthDate, phone, email).toDetails();
    }
}
