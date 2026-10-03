package com.clinic.schedule.portal.adapter.in.http;

import java.util.List;
import java.util.UUID;

import com.clinic.schedule.portal.application.usecase.ViewFamilyUseCase;
import com.clinic.schedule.portal.domain.AgeBand;

/**
 * A family group of the patient's, as they read it (D-85): each member's name, how old as the group's rules
 * count it — null without a date of birth —, whether the group sees their appointments, and which is the patient.
 * Nothing else of the others.
 */
record FamilyResponse(UUID id, String name, List<Member> members) {

    record Member(String fullName, AgeBand age, boolean shares, boolean me) {
    }

    static FamilyResponse of(ViewFamilyUseCase.Group group) {
        return new FamilyResponse(group.id(), group.name(), group.members().stream()
                .map(member -> new Member(member.fullName(), member.age(), member.shares(), member.me()))
                .toList());
    }
}
