package com.clinic.schedule.clinicalrecord.application.exception;

/** There is no copy of a record with that id or code. A 404: the request names nobody's content (D-95). */
public class RecordCopyNotFoundException extends RuntimeException {

    public RecordCopyNotFoundException() {
        super("There is no such copy of a record");
    }
}
