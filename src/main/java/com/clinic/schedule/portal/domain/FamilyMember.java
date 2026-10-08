package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A patient in a family group (D-84), and whether they share their appointments with it. A child under 13
 * shares always, whatever this says; from 13 it is their own choice, so a child added to a group starts with
 * it false and their appointments turn private on their thirteenth birthday, with nobody doing anything.
 *
 * <p>A child an adult registered on the web is added with the relation not proved, until reception sees what
 * proves it (D-92); every other child was added by reception with it seen.
 *
 * @param requestedBy the member who asked for a child to be added; null for whoever joined themselves
 * @param relation what that member is to the child; null with {@code requestedBy}
 * @param relationProved whether reception saw what proves the relation; true for whoever joined themselves
 */
public record FamilyMember(UUID patientId, boolean shares, UUID requestedBy, Relation relation,
        LocalDateTime addedAt, boolean relationProved) {

    /** What the member who asked for a child to be added is to them. */
    public enum Relation {
        MOTHER, FATHER, GUARDIAN
    }

    public FamilyMember {
        if (patientId == null || addedAt == null) {
            throw new IllegalArgumentException("A member is a patient, added at some time");
        }
        if ((requestedBy == null) != (relation == null)) {
            throw new IllegalArgumentException("A child is added at the request of a parent or guardian, named");
        }
        if (!relationProved && requestedBy == null) {
            throw new IllegalArgumentException("Only a child added at someone's request has a relation to prove");
        }
    }

    FamilyMember sharing(boolean shares) {
        return new FamilyMember(patientId, shares, requestedBy, relation, addedAt, relationProved);
    }

    FamilyMember proved() {
        return new FamilyMember(patientId, shares, requestedBy, relation, addedAt, true);
    }
}
