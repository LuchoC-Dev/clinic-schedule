package com.clinic.schedule.portal.application.exception;

/** The record already has a copy waiting to be authenticated: one is asked for at a time (D-95). A 409. */
public class RecordCopyWaitingException extends RuntimeException {

    public RecordCopyWaitingException() {
        super("The record already has a copy waiting to be authenticated");
    }
}
