package com.clinic.schedule.portal.application.port;

import java.util.List;
import java.util.UUID;

import com.clinic.schedule.portal.domain.FamilyChange;

/** Where the history of each family group is kept. Only ever added to. */
public interface FamilyChangeRepository {

    void save(FamilyChange change);

    /** The group's history, the most recent first. */
    List<FamilyChange> findForGroup(UUID groupId);
}
