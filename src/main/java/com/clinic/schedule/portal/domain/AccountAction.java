package com.clinic.schedule.portal.domain;

/** What was done to a web account, as its history keeps it (D-79). */
public enum AccountAction {
    OPENED,
    ACTIVATION_SENT,
    ACTIVATED,
    EMAIL_CHANGED,
    DEACTIVATED,
    REACTIVATED
}
