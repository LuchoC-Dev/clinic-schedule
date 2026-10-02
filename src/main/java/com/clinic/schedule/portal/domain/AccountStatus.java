package com.clinic.schedule.portal.domain;

/**
 * Where a patient's web account stands (D-79): opened by reception and waiting for the patient to choose
 * their password, in use, or switched off by reception.
 */
public enum AccountStatus {
    PENDING,
    ACTIVE,
    INACTIVE
}
