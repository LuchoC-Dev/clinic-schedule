package com.clinic.schedule.portal.application.exception;

import java.util.UUID;

/** No family group with that id. Maps to 404. */
public class FamilyGroupNotFoundException extends RuntimeException {

    public FamilyGroupNotFoundException(UUID id) {
        super("No family group " + id);
    }
}
