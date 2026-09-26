package com.clinic.schedule.arrival.session.command;

/** Only a practitioner calls patients. The routes already keep everyone else out. */
public class NotAPractitionerException extends RuntimeException {

    NotAPractitionerException() {
        super("Only a practitioner calls patients");
    }
}
