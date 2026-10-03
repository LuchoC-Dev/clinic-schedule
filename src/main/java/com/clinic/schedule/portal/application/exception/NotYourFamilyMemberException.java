package com.clinic.schedule.portal.application.exception;

import java.util.UUID;

/**
 * A patient asked to act for someone they do not act for (D-85): not a member of a group of theirs, one whose
 * appointments are private, or anyone while the patient is under 18. Maps to 403 (D-50).
 */
public class NotYourFamilyMemberException extends RuntimeException {

    public NotYourFamilyMemberException(UUID patientId) {
        super("The patient logged in does not act for patient " + patientId);
    }
}
