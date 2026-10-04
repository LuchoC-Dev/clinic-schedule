package com.clinic.schedule.portal.application.exception;

/**
 * A link to choose a new password that leads nowhere (D-86): never issued, used already, replaced by a newer
 * one or expired. Maps to 400; the screen says the link no longer works and how to ask for another.
 */
public class InvalidResetLinkException extends RuntimeException {

    public InvalidResetLinkException() {
        super("The link does not reset any password");
    }
}
