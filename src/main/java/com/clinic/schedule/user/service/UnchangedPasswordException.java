package com.clinic.schedule.user.service;

/** A new password the same as the current one: a temporary password would stay the admin's (D-69). */
public class UnchangedPasswordException extends RuntimeException {

    public UnchangedPasswordException() {
        super("The new password must differ from the current one");
    }
}
