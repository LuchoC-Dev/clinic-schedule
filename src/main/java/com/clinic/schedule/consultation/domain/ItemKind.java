package com.clinic.schedule.consultation.domain;

/**
 * What an item of a consultation is. Each kind is free text today; giving one of them a
 * structure later touches that kind alone (D-47).
 */
public enum ItemKind {

    /** A check done during the consultation: pulse, blood pressure, an electrocardiogram… */
    CHECK,

    /** A prescription given to the patient. */
    PRESCRIPTION,

    /** An order for a study — an X-ray, a Doppler ultrasound… — here or elsewhere. */
    STUDY_ORDER
}
