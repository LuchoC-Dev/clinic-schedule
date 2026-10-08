package com.clinic.schedule.portal.application.exception;

/**
 * A link that confirms no registration made on the web: unknown, expired, or used (D-91). Maps to 400. Which of
 * them it was is not said: the link is all the asker has.
 */
public class InvalidRegistrationLinkException extends RuntimeException {

    public InvalidRegistrationLinkException() {
        super("The link to confirm the registration is unknown, expired or used");
    }
}
