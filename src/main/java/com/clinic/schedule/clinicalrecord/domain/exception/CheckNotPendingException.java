package com.clinic.schedule.clinicalrecord.domain.exception;

import com.clinic.schedule.clinicalrecord.domain.CheckId;

/** Access is asked to report a check, and this one already has its result (D-54). A 409. */
public class CheckNotPendingException extends RuntimeException {

    public CheckNotPendingException(CheckId id) {
        super("Check " + id.value() + " already has its result: there is nothing to report");
    }
}
