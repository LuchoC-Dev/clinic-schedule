package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.domain.FamilyChange;

/** An entry of a family group's history; {@code patientId} is null for the group's creation. */
record FamilyChangeResponse(FamilyChange.Action action, UUID patientId, LocalDateTime at, String by) {

    static FamilyChangeResponse of(FamilyChange change) {
        return new FamilyChangeResponse(change.action(), change.patientId(), change.at(), change.by());
    }
}
