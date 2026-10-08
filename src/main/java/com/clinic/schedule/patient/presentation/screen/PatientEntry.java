package com.clinic.schedule.patient.presentation.screen;

import java.util.UUID;

import com.clinic.schedule.patient.entity.DocumentType;
import com.clinic.schedule.patient.entity.IdentityDocument;
import com.clinic.schedule.patient.entity.IdentityStatus;
import com.clinic.schedule.patient.entity.Patient;
import com.clinic.schedule.patient.entity.RegistrationChannel;

/**
 * A patient as the screens show them, with their identity in Spanish (D-60).
 *
 * @param document a label such as "DNI 30111222", or null
 * @param alias the alias they went by, when they had no name at first; null otherwise
 * @param registeredOnWeb whether they registered themselves on the web (D-91)
 */
record PatientEntry(UUID id, String fullName, String document, DocumentType documentType, String documentNumber,
        String identity, boolean verified, String alias, boolean registeredOnWeb) {

    static PatientEntry of(Patient patient) {
        IdentityDocument document = patient.getDocument();
        return new PatientEntry(patient.getId(), patient.getFullName(), label(document),
                document == null ? null : document.type(), document == null ? null : document.number(),
                identity(patient.getIdentityStatus()), patient.getIdentityStatus() == IdentityStatus.VERIFIED,
                patient.getAlias(), patient.getRegisteredVia() == RegistrationChannel.WEB);
    }

    static String label(IdentityDocument document) {
        return document == null ? null : document.type() + " " + document.number();
    }

    static String identity(IdentityStatus status) {
        return switch (status) {
            case ALIAS -> "Sin datos";
            case DECLARED -> "No verificada";
            case VERIFIED -> "Verificada";
        };
    }
}
