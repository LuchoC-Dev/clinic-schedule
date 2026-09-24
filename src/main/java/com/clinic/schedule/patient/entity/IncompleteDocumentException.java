package com.clinic.schedule.patient.entity;

/**
 * Half a document was given: a type without a number, or a number without a type. Maps to
 * 400, since the request itself does not make sense.
 */
public class IncompleteDocumentException extends RuntimeException {

    IncompleteDocumentException() {
        super("A document needs both a type and a number, or neither");
    }
}
