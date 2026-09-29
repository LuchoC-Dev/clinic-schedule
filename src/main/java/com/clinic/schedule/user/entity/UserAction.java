package com.clinic.schedule.user.entity;

/** What an admin did to a user (D-68), or the user did to their own password (D-69). */
public enum UserAction {
    CREATED,
    DEACTIVATED,
    REACTIVATED,
    PASSWORD_RESET,
    PASSWORD_CHANGED
}
