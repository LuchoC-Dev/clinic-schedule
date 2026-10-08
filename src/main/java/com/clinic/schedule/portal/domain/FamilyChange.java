package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One entry of a family group's history (D-84): what happened, to which member, when, and by whom — reception's
 * username, or the patient's account (D-81). Only {@link FamilyGroup} makes one, and nothing changes one once
 * made.
 *
 * @param patientId the member it is about; null for the group's own creation
 */
public record FamilyChange(UUID id, UUID groupId, Action action, UUID patientId, LocalDateTime at, String by) {

    public enum Action {
        CREATED,
        JOINED,
        CHILD_ADDED,
        REMOVED,
        SHARING_STARTED,
        SHARING_STOPPED,
        /** Reception saw what proves the relation of a child an adult registered on the web (D-92). */
        RELATION_PROVED
    }

    public FamilyChange {
        if (id == null || groupId == null || action == null || at == null || by == null || by.isBlank()) {
            throw new IllegalArgumentException("A change to a family group needs its group, what, when and who");
        }
        if ((action == Action.CREATED) != (patientId == null)) {
            throw new IllegalArgumentException("A change to a family group is about a member, but its creation");
        }
    }

    static FamilyChange of(UUID groupId, Action action, UUID patientId, LocalDateTime at, String by) {
        return new FamilyChange(UUID.randomUUID(), groupId, action, patientId, at, by);
    }
}
