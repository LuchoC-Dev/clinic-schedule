package com.clinic.schedule.practitioner;

/** The practitioner already has a reason with that name, ignoring case. Maps to 409. */
class DuplicateReasonException extends RuntimeException {

    DuplicateReasonException(String name) {
        super("The practitioner already has a reason named " + name.strip());
    }
}
