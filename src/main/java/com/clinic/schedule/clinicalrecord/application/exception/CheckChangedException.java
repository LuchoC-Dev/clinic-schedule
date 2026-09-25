package com.clinic.schedule.clinicalrecord.application.exception;

import com.clinic.schedule.clinicalrecord.domain.CheckId;

/**
 * Someone else changed the check between reading it and saving it — two addenda at the same time,
 * or an addendum and its result. Nothing was saved; sending it again works. A 409 (D-58).
 */
public class CheckChangedException extends RuntimeException {

    public CheckChangedException(CheckId id) {
        super("Check " + id.value() + " changed meanwhile; send it again");
    }
}
