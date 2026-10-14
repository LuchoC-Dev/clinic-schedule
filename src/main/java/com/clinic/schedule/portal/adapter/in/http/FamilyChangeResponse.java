package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.portal.domain.FamilyChange;

/** An entry of a family group's history; {@code patientId} is null for the group's creation. */
record FamilyChangeResponse(FamilyChange.Action action, UUID patientId, String patient, LocalDateTime at,
        String by) {

    /** {@code patient} is the name of whom the change was about, members who left included (D-102). */
    static FamilyChangeResponse of(FamilyChange change, Map<UUID, PatientSummary> patients) {
        PatientSummary patient = change.patientId() == null ? null : patients.get(change.patientId());
        return new FamilyChangeResponse(change.action(), change.patientId(), patient == null ? null : patient.fullName(),
                change.at(), change.by());
    }
}
