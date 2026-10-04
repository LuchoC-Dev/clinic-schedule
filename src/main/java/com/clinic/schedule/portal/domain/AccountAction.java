package com.clinic.schedule.portal.domain;

/** What was done to a web account, as its history keeps it (D-79, D-86). */
public enum AccountAction {
    OPENED,
    ACTIVATION_SENT,
    ACTIVATED,
    EMAIL_CHANGED,
    DEACTIVATED,
    REACTIVATED,
    /** A link to choose a new password went out, asked for from the web by whoever typed the document. */
    RECOVERY_SENT,
    /** The patient chose a new password from that link. */
    PASSWORD_RESET,
    /** The patient changed their password, logged in. */
    PASSWORD_CHANGED,
    /** Too many failed logins in a row locked the account for a while. */
    LOCKED
}
