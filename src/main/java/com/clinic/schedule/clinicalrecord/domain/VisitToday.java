package com.clinic.schedule.clinicalrecord.domain;

/**
 * Whether a practitioner is seeing a patient today. Where the answer comes from — today, the
 * appointments and their check-in — is not this module's business (D-48).
 */
public enum VisitToday {

    /** The practitioner is not seeing the patient today. */
    NONE,

    /** The patient is expected today, and has not arrived yet. */
    EXPECTED,

    /** The patient has arrived to be seen today. */
    ARRIVED
}
