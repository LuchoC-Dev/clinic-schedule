package com.clinic.schedule.patient.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * What reminding a patient needs (D-78): their name — unless they go by an alias —, their date of
 * birth, and their email, whose it is and who agreed to its use. Never more than that: no document,
 * no phone, no insurance.
 *
 * @param fullName null for a patient under an alias, who is not to be named
 * @param birthDate null when not given
 * @param email null when the patient has none
 */
public record PatientContact(UUID id, String fullName, LocalDate birthDate, Channel email) {

    /**
     * A way to reach the patient.
     *
     * @param ownerName the responsible person it belongs to; null when it is the patient's own
     * @param consent who agreed to the patient being reminded there; null for nobody
     */
    public record Channel(String address, String ownerName, ConsentGiver consent) {
    }

    /** Who agreed to the patient being reminded on a channel (D-77). */
    public enum ConsentGiver {
        PATIENT, OWNER
    }
}
