package com.clinic.schedule.portal.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * For whom, besides themselves, a patient books, cancels and sees appointments on the web (D-85): the members
 * of a group of theirs whose appointments the group sees — a child under 13 always, anyone else by their own
 * choice —, as long as the patient is 18 or older. A member under 18 acts only for themselves. Whether the
 * patient shares their own appointments does not matter: it says whether the others see theirs. A child whose
 * relation is not proved yet — registered on the web — is acted for only by the adult who registered them,
 * until reception proves it (D-92).
 *
 * <p>Worked out at each request from the groups and the dates of birth it is handed, and never kept, so a child's
 * appointments stop being seen on their thirteenth birthday with nobody doing anything.
 */
public final class FamilyAccessPolicy {

    private FamilyAccessPolicy() {
    }

    /**
     * @param groups the groups the patient is in; any other is ignored
     * @param birthDates the members' dates of birth, those without one left out
     * @return the members they act for, never themselves
     */
    public static Set<UUID> actedFor(FamilyPerson patient, Collection<FamilyGroup> groups,
            Map<UUID, LocalDate> birthDates, LocalDate today) {
        Set<UUID> members = new HashSet<>();
        if (patient.birthDate() == null || AgeBand.of(patient.birthDate(), today) != AgeBand.ADULT) {
            return members;
        }
        for (FamilyGroup group : groups) {
            if (group.member(patient.patientId()).isEmpty()) {
                continue;
            }
            for (FamilyMember member : group.members()) {
                if (!member.relationProved() && !patient.patientId().equals(member.requestedBy())) {
                    continue;
                }
                if (!member.patientId().equals(patient.patientId()) && FamilyGroupService.sharesOn(member,
                        new FamilyPerson(member.patientId(), birthDates.get(member.patientId())), today)) {
                    members.add(member.patientId());
                }
            }
        }
        return members;
    }
}
