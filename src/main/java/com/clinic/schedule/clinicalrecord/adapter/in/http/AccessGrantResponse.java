package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.AccessGrant;
import com.clinic.schedule.clinicalrecord.domain.Approval;

/** An access grant as the API shows it: who, for which patient and check, when, until when, why, how (D-54). */
record AccessGrantResponse(
        UUID id,
        UUID patientId,
        UUID practitionerId,
        String practitionerName,
        UUID checkId,
        LocalDateTime grantedAt,
        LocalDate validUntil,
        String reason,
        Approval approval) {

    /** @param names every practitioner's name by id; an unknown one is left null */
    static AccessGrantResponse of(AccessGrant grant, Map<UUID, String> names) {
        return new AccessGrantResponse(
                grant.id().value(),
                grant.patientId().value(),
                grant.practitionerId().value(),
                names.get(grant.practitionerId().value()),
                grant.checkId().value(),
                grant.grantedAt(),
                grant.validUntil(),
                grant.reason().orElse(null),
                grant.approval());
    }
}
