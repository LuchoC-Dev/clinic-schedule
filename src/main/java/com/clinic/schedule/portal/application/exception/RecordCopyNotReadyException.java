package com.clinic.schedule.portal.application.exception;

/** The copy cannot be downloaded: it goes on paper, or a practitioner has not authenticated it yet (D-95). A 409. */
public class RecordCopyNotReadyException extends RuntimeException {

    public RecordCopyNotReadyException() {
        super("The copy of the record cannot be downloaded");
    }
}
