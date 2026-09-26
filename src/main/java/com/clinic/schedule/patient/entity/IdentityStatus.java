package com.clinic.schedule.patient.entity;

/**
 * How far the practice knows who a patient is (D-60). One state rather than separate flags, so
 * that no patient can be both nameless and verified.
 */
public enum IdentityStatus {

    /** They gave no name, and go by an alias such as NN-4821. */
    ALIAS,

    /** They gave a name, and maybe a document, that nobody has seen. */
    DECLARED,

    /** Reception saw the document they identify themselves with. */
    VERIFIED
}
