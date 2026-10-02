package com.clinic.schedule.portal.domain;

import java.time.LocalDate;

/**
 * A patient's data as they read it on their web account (D-81): their name, document and date of birth,
 * which reception keeps, and their phone and email — whose each is, and who agreed to be reminded there —
 * and their health insurance, which they change themselves.
 *
 * @param birthDate null when not given
 * @param phone null when there is none
 * @param email null when there is none
 * @param insurance null when there is none
 */
public record OwnData(String fullName, String document, LocalDate birthDate, Channel phone, Channel email,
        Insurance insurance) {

    /**
     * A phone or an email.
     *
     * @param ownerName null when it is the patient's own
     * @param ownerRelation such as "MOTHER"; null when it is the patient's own
     * @param consent null when nobody agreed to the patient being reminded there
     */
    public record Channel(String value, String ownerName, String ownerRelation, Consent consent) {
    }

    /** Who agreed to the patient being reminded on a channel: themselves, or the person it belongs to. */
    public enum Consent {
        PATIENT, OWNER
    }

    /** Each part null when not given. */
    public record Insurance(String insurer, String memberNumber, String plan) {
    }
}
