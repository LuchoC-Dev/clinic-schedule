package com.clinic.schedule.practitioner.entity;

/** The practitioner already has a reason with that name, ignoring case. Maps to 409. */
public class DuplicateReasonException extends RuntimeException {

    DuplicateReasonException(String name) {
        super("The practitioner already has a reason named " + name.strip());
    }
}
