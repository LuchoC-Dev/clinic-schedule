package com.clinic.schedule.portal.application.exception;

/**
 * An activation link that activates nothing: unknown, expired, or ended — used, replaced by another, or of
 * an account deactivated. Maps to 400. Which of them it was is not said: the link is all the asker has.
 */
public class InvalidActivationLinkException extends RuntimeException {

    public InvalidActivationLinkException() {
        super("The activation link is unknown, expired or used");
    }
}
