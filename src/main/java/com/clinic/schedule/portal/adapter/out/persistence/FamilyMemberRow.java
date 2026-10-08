package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.domain.FamilyMember;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** A member of a family group as a row of {@code family_member}, kept with its group (D-84). */
@Embeddable
class FamilyMemberRow {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private boolean shares;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private FamilyMember.Relation relation;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @Column(name = "relation_proved", nullable = false)
    private boolean relationProved;

    protected FamilyMemberRow() {
        // required by JPA
    }

    static FamilyMemberRow from(FamilyMember member) {
        FamilyMemberRow row = new FamilyMemberRow();
        row.patientId = member.patientId();
        row.shares = member.shares();
        row.requestedBy = member.requestedBy();
        row.relation = member.relation();
        row.addedAt = member.addedAt();
        row.relationProved = member.relationProved();
        return row;
    }

    FamilyMember toMember() {
        return new FamilyMember(patientId, shares, requestedBy, relation, addedAt, relationProved);
    }
}
