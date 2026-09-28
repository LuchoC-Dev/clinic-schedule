package com.clinic.schedule.user.service;

import java.util.UUID;

public class UnknownPractitionerException extends RuntimeException {

    public UnknownPractitionerException(UUID practitionerId) {
        super("No practitioner with id " + practitionerId);
    }
}
