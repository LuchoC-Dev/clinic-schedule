package com.clinic.schedule.reminder.domain;

/** What happened to a reminder (D-78). */
public enum ReminderOutcome {

    /** It went out. */
    SENT,

    /** It was tried, once, and did not go out: reception is to call the patient instead. */
    FAILED,

    /** It was not sent, for a reason of its own. */
    NOT_SENT
}
