package com.clinic.schedule.portal.domain;

/** A family group as it became, and the entry that records how (D-84): no change is made without one. */
public record FamilyUpdate(FamilyGroup group, FamilyChange change) {
}
