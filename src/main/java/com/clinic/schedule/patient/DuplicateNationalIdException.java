package com.clinic.schedule.patient;

/** That national ID already belongs to another patient. Maps to 409. */
class DuplicateNationalIdException extends RuntimeException {

    DuplicateNationalIdException(String nationalId) {
        super("National ID " + nationalId + " already belongs to another patient");
    }
}
