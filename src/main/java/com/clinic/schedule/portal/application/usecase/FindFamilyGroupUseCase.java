package com.clinic.schedule.portal.application.usecase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.FamilyGroupNotFoundException;
import com.clinic.schedule.portal.application.port.FamilyChangeRepository;
import com.clinic.schedule.portal.application.port.FamilyGroupRepository;
import com.clinic.schedule.portal.domain.FamilyChange;
import com.clinic.schedule.portal.domain.FamilyGroup;

/** The family groups as reception reads them, with their history (D-84). Reception's alone, by route (D-34). */
@Service
public class FindFamilyGroupUseCase {

    private final FamilyGroupRepository groups;
    private final FamilyChangeRepository changes;

    public FindFamilyGroupUseCase(FamilyGroupRepository groups, FamilyChangeRepository changes) {
        this.groups = groups;
        this.changes = changes;
    }

    /** Every group, by name, or the patient's when one is given. */
    @Transactional(readOnly = true)
    public List<FamilyGroup> list(UUID patientId) {
        return patientId == null ? groups.findAll() : groups.findOf(patientId);
    }

    /** @throws FamilyGroupNotFoundException when there is no such group */
    @Transactional(readOnly = true)
    public FamilyGroup byId(UUID groupId) {
        return groups.findById(groupId).orElseThrow(() -> new FamilyGroupNotFoundException(groupId));
    }

    /** What happened to the group, the most recent first. */
    @Transactional(readOnly = true)
    public List<FamilyChange> history(UUID groupId) {
        byId(groupId);
        return changes.findForGroup(groupId);
    }
}
