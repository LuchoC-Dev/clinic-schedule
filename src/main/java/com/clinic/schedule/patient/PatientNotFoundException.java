package com.clinic.schedule.patient;

import java.util.UUID;

/** No patient with that id. Maps to 404. */
class PatientNotFoundException extends RuntimeException {

    PatientNotFoundException(UUID id) {
        super("There is no patient with id " + id);
    }
}
