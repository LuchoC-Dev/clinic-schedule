package com.clinic.schedule.clinicalrecord.application.exception;

import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;

/** Someone else changed the copy meanwhile — two practitioners authenticating it at once. A 409. */
public class RecordCopyChangedException extends RuntimeException {

    public RecordCopyChangedException(RecordCopyId id) {
        super("The copy " + id.value() + " was changed meanwhile");
    }
}
