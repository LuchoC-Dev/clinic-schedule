package com.clinic.schedule.patient.api;

import java.time.LocalDate;

/**
 * A patient's data as they read it themselves, on their web account (D-81): who they are, which they do not
 * change, and how to reach them, who agreed to be reminded where, and their health insurance, which they do.
 * Nothing clinical, and nothing of reception's own.
 *
 * @param document a ready-to-read label such as "DNI 30111222"
 * @param birthDate null when not given
 * @param phone null when there is none
 * @param email null when there is none
 * @param insurance null when there is none
 */
public record PatientProfile(String fullName, String document, LocalDate birthDate, Channel phone, Channel email,
        Insurance insurance) {

    /**
     * A phone or an email, and whose it is.
     *
     * @param ownerName the responsible person it belongs to; null when it is the patient's own
     * @param ownerRelation what they are to the patient, such as "MOTHER"; null when it is the patient's own
     * @param consent who agreed to the patient being reminded there; null for nobody
     */
    public record Channel(String value, String ownerName, String ownerRelation, ConsentGiver consent) {
    }

    /** Who agreed to the patient being reminded on a channel (D-77). */
    public enum ConsentGiver {
        PATIENT, OWNER
    }

    /** The insurer, and the member number and plan, each null when not given. */
    public record Insurance(String insurer, String memberNumber, String plan) {
    }
}
