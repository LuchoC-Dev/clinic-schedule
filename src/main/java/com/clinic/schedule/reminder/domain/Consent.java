package com.clinic.schedule.reminder.domain;

/** Who agreed to the patient being reminded on a channel (D-77). */
public enum Consent {

    /** The patient, on their own channel or on a responsible person's. */
    PATIENT,

    /** The responsible person the channel belongs to. */
    OWNER
}
