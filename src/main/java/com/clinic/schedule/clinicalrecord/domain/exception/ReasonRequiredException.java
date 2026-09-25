package com.clinic.schedule.clinicalrecord.domain.exception;

/** Access was asked without a reason, and the practice requires one (D-54). A 400. */
public class ReasonRequiredException extends RuntimeException {

    public ReasonRequiredException() {
        super("A reason is required to ask for access to a clinical record");
    }
}
