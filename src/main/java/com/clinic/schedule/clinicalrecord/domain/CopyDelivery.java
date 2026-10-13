package com.clinic.schedule.clinicalrecord.domain;

/** How the patient chose to get the copy of their record (D-95). */
public enum CopyDelivery {

    /** A PDF they download from their account, once authenticated. */
    PDF,

    /**
     * On paper: the practitioner prints it, signs and stamps it, and leaves it with reception in a sealed
     * envelope, for the patient to collect showing their document.
     */
    PAPER
}
