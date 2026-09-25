package com.clinic.schedule.clinicalrecord.domain;

/**
 * What an item of a consultation is. Each kind is free text today; giving one of them a
 * structure later touches that kind alone (D-47).
 *
 * <p>Only prescriptions are left. Checks became an aggregate of their own (D-53), and orders for a
 * check as well, since a check fulfils them later (D-55).
 */
public enum ItemKind {

    /** A prescription given to the patient. */
    PRESCRIPTION
}
