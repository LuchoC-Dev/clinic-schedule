package com.clinic.schedule.clinicalrecord.api;

/** How the patient chooses to get the copy of a record (D-95). */
public enum CopyDeliveryChoice {

    /** A PDF to download from the account, once authenticated. */
    PDF,

    /** On paper, signed and stamped, collected at reception showing one's document. */
    PAPER
}
