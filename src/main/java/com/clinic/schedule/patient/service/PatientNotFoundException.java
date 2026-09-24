package com.clinic.schedule.patient.service;

import java.util.UUID;

/** No patient with that id. Maps to 404. */
public class PatientNotFoundException extends RuntimeException {

    PatientNotFoundException(UUID id) {
        super("There is no patient with id " + id);
    }
}
