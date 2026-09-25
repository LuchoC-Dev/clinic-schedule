package com.clinic.schedule.clinicalrecord.application.exception;

import com.clinic.schedule.clinicalrecord.domain.CheckId;

/**
 * Access was asked to report a check that does not exist. A 404: the pending checks are listed to
 * every practitioner, so saying one does not exist tells nothing (D-54).
 */
public class CheckNotFoundException extends RuntimeException {

    public CheckNotFoundException(CheckId id) {
        super("There is no check " + id.value());
    }
}
