package com.clinic.schedule.clinicalrecord.api;

/** The copy cannot be downloaded: it goes on paper, or it is not authenticated yet (D-95). */
public class RecordCopyNotReadyException extends RuntimeException {

    public RecordCopyNotReadyException() {
        super("The copy of the record cannot be downloaded");
    }
}
