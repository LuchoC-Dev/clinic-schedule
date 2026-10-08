package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.portal.domain.FamilyGroup;
import com.clinic.schedule.portal.domain.FamilyGroupService;
import com.clinic.schedule.portal.domain.FamilyMember;
import com.clinic.schedule.portal.domain.FamilyPerson;

/**
 * A family group as reception reads it (D-84): its members, each with whether the group sees their
 * appointments today — always for a child under 13 — and, for a child, who asked for them to be added and
 * whether that relation is proved: a child an adult registered on the web waits for reception to see it (D-92).
 */
record FamilyGroupResponse(UUID id, String name, LocalDateTime createdAt, List<Member> members) {

    /** {@code requestedBy} and {@code relation} are null for whoever joined themselves. */
    record Member(UUID patientId, String fullName, boolean shares, UUID requestedBy, FamilyMember.Relation relation,
            boolean relationProved) {
    }

    static FamilyGroupResponse of(FamilyGroup group, Map<UUID, PatientSummary> patients, LocalDate today) {
        return new FamilyGroupResponse(group.id(), group.name(), group.createdAt(), group.members().stream()
                .map(member -> {
                    PatientSummary patient = patients.get(member.patientId());
                    return new Member(member.patientId(), patient.fullName(),
                            FamilyGroupService.sharesOn(member, new FamilyPerson(member.patientId(),
                                    patient.birthDate()), today),
                            member.requestedBy(), member.relation(), member.relationProved());
                })
                .toList());
    }
}
