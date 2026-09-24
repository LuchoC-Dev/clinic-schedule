package com.clinic.schedule.patient.service;

import com.clinic.schedule.patient.entity.IdentityDocument;

/** That document already belongs to another patient. Maps to 409. */
public class DuplicateDocumentException extends RuntimeException {

    DuplicateDocumentException(IdentityDocument document) {
        super("%s %s already belongs to another patient".formatted(document.type(), document.number()));
    }
}
