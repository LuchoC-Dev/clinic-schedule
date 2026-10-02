package com.clinic.schedule.reminder.domain;

/** Why a reminder was not sent (D-78). */
public enum NotSentReason {

    /** The patient has no email. */
    NO_EMAIL,

    /** Nobody agreed to the patient being reminded at their email. */
    NO_CONSENT,

    /**
     * The email is a responsible person's, who agreed on their own, and the patient is 13 or older — or
     * their age is not known —, so a reminder might tell them of a visit the patient keeps to themselves.
     */
    CONFIDENTIAL
}
