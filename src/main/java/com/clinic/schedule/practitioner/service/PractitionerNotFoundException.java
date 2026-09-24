package com.clinic.schedule.practitioner.service;

import java.util.UUID;

/** No practitioner with that id. Maps to 404. */
public class PractitionerNotFoundException extends RuntimeException {

    PractitionerNotFoundException(UUID id) {
        super("There is no practitioner with id " + id);
    }
}
