package com.clinic.schedule.clinicalrecord.api;

/** The record already has a copy waiting to be authenticated: one is asked for at a time (D-95). */
public class RecordCopyAlreadyRequestedException extends RuntimeException {

    public RecordCopyAlreadyRequestedException() {
        super("The record already has a copy waiting to be authenticated");
    }
}
