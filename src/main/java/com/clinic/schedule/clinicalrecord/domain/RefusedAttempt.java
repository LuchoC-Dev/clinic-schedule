package com.clinic.schedule.clinicalrecord.domain;

/** What someone tried to reach and was refused (D-72). */
public enum RefusedAttempt {

    /** A patient's clinical record, which the practitioner may not read. */
    RECORD,

    /** A file attached to a record the practitioner may not read, or that does not exist. */
    ATTACHMENT,

    /** A clinical route, which the user's role does not reach. */
    ROUTE
}
