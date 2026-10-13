package com.clinic.schedule.portal.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Whose clinical record a patient may ask a copy of on the web (D-95): their own, from 13 — a patient that age
 * decides alone on much of their own care (Código Civil y Comercial, art. 26), and the account is theirs from then
 * (D-79) —; and that of each child under 13 whose relation with them reception proved: the member of a group of
 * theirs they asked to add, as mother, father or guardian, with the proof seen (D-84, D-92). Not the children
 * another adult of the group asked for, nor one whose relation is not proved yet, nor anyone of 13 or older, whose
 * record is theirs alone (Ley 26.529, art. 2 d). A spouse, a partner or an heir asks at reception (art. 19).
 *
 * <p>Worked out at each request, never kept: a child's record stops being their parent's to ask for on their
 * thirteenth birthday, with nobody doing anything — the PDF of a copy asked before then included.
 */
public final class RecordCopyPolicy {

    private RecordCopyPolicy() {
    }

    /**
     * @param groups the groups the patient is in; any other is ignored
     * @param birthDates the members' dates of birth, those without one left out
     * @return the patient first, when they may ask for their own, then the children
     */
    public static Set<UUID> askableFor(FamilyPerson patient, Collection<FamilyGroup> groups,
            Map<UUID, LocalDate> birthDates, LocalDate today) {
        Set<UUID> whom = new LinkedHashSet<>();
        if (patient.birthDate() != null && AgeBand.of(patient.birthDate(), today) != AgeBand.CHILD) {
            whom.add(patient.patientId());
        }
        for (FamilyGroup group : groups) {
            if (group.member(patient.patientId()).isEmpty()) {
                continue;
            }
            for (FamilyMember member : group.members()) {
                LocalDate birthDate = birthDates.get(member.patientId());
                if (!member.patientId().equals(patient.patientId())
                        && patient.patientId().equals(member.requestedBy())
                        && member.relationProved()
                        && birthDate != null && AgeBand.of(birthDate, today) == AgeBand.CHILD) {
                    whom.add(member.patientId());
                }
            }
        }
        return whom;
    }
}
