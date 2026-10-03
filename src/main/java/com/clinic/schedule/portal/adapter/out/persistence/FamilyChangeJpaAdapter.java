package com.clinic.schedule.portal.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.portal.application.port.FamilyChangeRepository;
import com.clinic.schedule.portal.domain.FamilyChange;

/** Implements the port over JPA. */
@Repository
class FamilyChangeJpaAdapter implements FamilyChangeRepository {

    private final FamilyChangeRowRepository rows;

    FamilyChangeJpaAdapter(FamilyChangeRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(FamilyChange change) {
        rows.save(FamilyChangeRow.from(change));
    }

    @Override
    public List<FamilyChange> findForGroup(UUID groupId) {
        return rows.findByGroupIdOrderByChangedAtDesc(groupId).stream().map(FamilyChangeRow::toChange).toList();
    }
}
