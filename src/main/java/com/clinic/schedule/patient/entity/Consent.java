package com.clinic.schedule.patient.entity;

/**
 * Who agreed to the patient being reminded on one of their channels, the phone or the email (D-77).
 * No consent at all is none of these: the patient is not to be reminded there.
 */
public enum Consent {

    /** The patient, on their own channel or on a responsible person's. */
    PATIENT,

    /** The responsible person the channel belongs to: a child's mother, for one. */
    OWNER
}
