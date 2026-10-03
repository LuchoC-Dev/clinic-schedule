package com.clinic.schedule.portal.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.portal.domain.FamilyGroup;

/** Where the family groups are kept, with their members (D-84). */
public interface FamilyGroupRepository {

    void save(FamilyGroup group);

    Optional<FamilyGroup> findById(UUID id);

    /** Every group, by name. */
    List<FamilyGroup> findAll();

    /** The groups the patient is in. */
    List<FamilyGroup> findOf(UUID patientId);
}
