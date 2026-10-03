package com.clinic.schedule.portal.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.port.CurrentPatientPort;

/**
 * The patient logged in, from 13 on, chooses whether a group of theirs sees their appointments, or leaves it
 * (D-85). Only about themselves: a group's rules are {@link ManageFamilyGroupUseCase}'s, and the change is
 * recorded as made by their account. Reception alone takes a child out of a group.
 */
@Service
public class ManageOwnFamilyUseCase {

    private final CurrentPatientPort currentPatient;
    private final ManageFamilyGroupUseCase groups;

    public ManageOwnFamilyUseCase(CurrentPatientPort currentPatient, ManageFamilyGroupUseCase groups) {
        this.currentPatient = currentPatient;
        this.groups = groups;
    }

    /** @throws com.clinic.schedule.portal.domain.exception.FamilyRefusedException when they are not in it */
    @Transactional
    public void share(UUID groupId, boolean shares) {
        groups.share(groupId, currentPatient.patientId(), shares);
    }

    /** @throws com.clinic.schedule.portal.domain.exception.FamilyRefusedException when they are not in it */
    @Transactional
    public void leave(UUID groupId) {
        groups.remove(groupId, currentPatient.patientId());
    }
}
