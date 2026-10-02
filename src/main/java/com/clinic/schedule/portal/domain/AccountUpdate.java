package com.clinic.schedule.portal.domain;

/** A web account as it became, and the entry that records how (D-79): no change is made without one. */
public record AccountUpdate(PatientAccount account, AccountChange change) {
}
