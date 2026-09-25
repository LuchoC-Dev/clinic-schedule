package com.clinic.schedule.clinicalrecord.domain.exception;

import com.clinic.schedule.clinicalrecord.domain.CheckId;

/**
 * The check already has a result, recorded before or at the same time by someone else. A result
 * never changes and a check has one (D-53): nothing was saved. A 409.
 */
public class ResultAlreadyRecordedException extends RuntimeException {

    public ResultAlreadyRecordedException(CheckId id) {
        super("Check " + id.value() + " already has a result");
    }
}
