package com.clinic.schedule.user.service;

/** An admin deactivating themselves, which could leave the practice with no admin (D-68). */
public class SelfDeactivationException extends RuntimeException {

    public SelfDeactivationException() {
        super("An admin cannot deactivate themselves");
    }
}
