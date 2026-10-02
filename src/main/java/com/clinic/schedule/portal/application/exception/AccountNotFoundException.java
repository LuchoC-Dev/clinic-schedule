package com.clinic.schedule.portal.application.exception;

import java.util.UUID;

/** No web account with that id, or none for that patient. Maps to 404. */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID id) {
        super("No web account " + id);
    }
}
