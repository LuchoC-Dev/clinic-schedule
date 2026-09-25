package com.clinic.schedule.clinicalrecord.domain;

/**
 * How an access grant was approved. Only automatically today; who else may approve, and why, is
 * to be decided later, and each grant already says how it was approved (D-54).
 */
public enum Approval {

    /** Granted on asking, with no one deciding. */
    AUTOMATIC
}
