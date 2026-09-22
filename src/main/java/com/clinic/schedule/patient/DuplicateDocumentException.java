package com.clinic.schedule.patient;

/** That document already belongs to another patient. Maps to 409. */
class DuplicateDocumentException extends RuntimeException {

    DuplicateDocumentException(IdentityDocument document) {
        super("%s %s already belongs to another patient".formatted(document.type(), document.number()));
    }
}
