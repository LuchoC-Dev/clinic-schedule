package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.domain.FamilyChange;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An entry of a family group's history as a row (D-84). Only ever inserted. */
@Entity
@Table(name = "family_group_change")
class FamilyChangeRow {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FamilyChange.Action action;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    protected FamilyChangeRow() {
        // required by JPA
    }

    static FamilyChangeRow from(FamilyChange change) {
        FamilyChangeRow row = new FamilyChangeRow();
        row.id = change.id();
        row.groupId = change.groupId();
        row.action = change.action();
        row.patientId = change.patientId();
        row.changedAt = change.at();
        row.changedBy = change.by();
        return row;
    }

    FamilyChange toChange() {
        return new FamilyChange(id, groupId, action, patientId, changedAt, changedBy);
    }
}
