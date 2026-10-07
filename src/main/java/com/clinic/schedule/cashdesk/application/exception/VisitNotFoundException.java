package com.clinic.schedule.cashdesk.application.exception;

import java.util.UUID;

/** No visit with that id. Belongs here and not to the domain: looking things up is the application's job. */
public class VisitNotFoundException extends RuntimeException {

    public VisitNotFoundException(UUID id) {
        super("There is no visit with id " + id);
    }
}
