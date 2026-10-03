package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.portal.domain.FamilyGroup;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** A family group as a row, with its members in a table of their own, kept and read with it (D-84). */
@Entity
@Table(name = "family_group")
class FamilyGroupRow {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "family_member", joinColumns = @JoinColumn(name = "group_id"))
    private List<FamilyMemberRow> members = new ArrayList<>();

    protected FamilyGroupRow() {
        // required by JPA
    }

    static FamilyGroupRow from(FamilyGroup group) {
        FamilyGroupRow row = new FamilyGroupRow();
        row.id = group.id();
        row.name = group.name();
        row.createdAt = group.createdAt();
        row.members = new ArrayList<>(group.members().stream().map(FamilyMemberRow::from).toList());
        return row;
    }

    FamilyGroup toGroup() {
        return FamilyGroup.restore(id, name, createdAt, members.stream().map(FamilyMemberRow::toMember).toList());
    }
}
