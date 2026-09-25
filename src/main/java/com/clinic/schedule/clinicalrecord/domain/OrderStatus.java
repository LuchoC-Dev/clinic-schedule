package com.clinic.schedule.clinicalrecord.domain;

/** Where an order for a check stands (D-55). */
public enum OrderStatus {

    /** Issued, and no check has fulfilled it yet. */
    OPEN,

    /** A check fulfilled it. */
    FULFILLED,

    /** Its author cancelled it, with a reason. */
    CANCELLED
}
