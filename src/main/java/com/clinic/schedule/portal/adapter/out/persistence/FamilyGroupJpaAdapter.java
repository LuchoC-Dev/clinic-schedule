package com.clinic.schedule.portal.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.portal.application.port.FamilyGroupRepository;
import com.clinic.schedule.portal.domain.FamilyGroup;

/** Implements the port over JPA. */
@Repository
class FamilyGroupJpaAdapter implements FamilyGroupRepository {

    private final FamilyGroupRowRepository rows;

    FamilyGroupJpaAdapter(FamilyGroupRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(FamilyGroup group) {
        rows.save(FamilyGroupRow.from(group));
    }

    @Override
    public Optional<FamilyGroup> findById(UUID id) {
        return rows.findById(id).map(FamilyGroupRow::toGroup);
    }

    @Override
    public List<FamilyGroup> findAll() {
        return rows.findAllByOrderByNameAsc().stream().map(FamilyGroupRow::toGroup).toList();
    }

    @Override
    public List<FamilyGroup> findOf(UUID patientId) {
        return rows.findOf(patientId).stream().map(FamilyGroupRow::toGroup).toList();
    }
}
