package com.clinic.schedule.clinicalrecord.domain;

/**
 * Why a practitioner may read a patient's clinical record: one of the ways
 * {@link ClinicalRecordAccessPolicy} opens it, in the order it asks them (D-59).
 */
public enum ReadAccess {

    /** They are seeing the patient today, whether or not the patient has arrived yet. */
    VISIT_TODAY,

    /** They hold a valid grant, asked for to report a check. */
    ACCESS_GRANT,

    /** They saw the patient within the access period. */
    SEEN_WITHIN_PERIOD
}
