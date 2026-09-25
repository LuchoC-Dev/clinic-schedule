package com.clinic.schedule.clinicalrecord.domain;

/**
 * What an item of a consultation is. Each kind is free text today; giving one of them a
 * structure later touches that kind alone (D-47).
 *
 * <p>Checks are not items any more: a check is done once and reported once, maybe later and by
 * someone else, so it is an aggregate of its own, {@link Check} (D-53).
 */
public enum ItemKind {

    /** A prescription given to the patient. */
    PRESCRIPTION,

    /** An order for a study — an X-ray, a Doppler ultrasound… — here or elsewhere. */
    STUDY_ORDER
}
