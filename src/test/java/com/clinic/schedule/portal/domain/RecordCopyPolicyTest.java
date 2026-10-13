package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Whose record a patient asks a copy of on the web (D-95): their own from 13, and their children under 13 whose
 * relation with them reception proved. "Today" is 10 March 2026.
 */
class RecordCopyPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);
    private static final LocalDateTime ADDED = LocalDateTime.of(2026, 1, 5, 10, 0);

    private static final UUID MOTHER = UUID.randomUUID();
    private static final UUID FATHER = UUID.randomUUID();
    private static final UUID CHILD = UUID.randomUUID();
    private static final UUID UNPROVED_CHILD = UUID.randomUUID();
    private static final UUID FATHERS_CHILD = UUID.randomUUID();
    private static final UUID TEEN = UUID.randomUUID();
    private static final UUID ALMOST_THIRTEEN = UUID.randomUUID();

    private static final Map<UUID, LocalDate> BIRTH_DATES = Map.of(
            MOTHER, LocalDate.of(1985, 4, 2),
            FATHER, LocalDate.of(1983, 1, 15),
            CHILD, LocalDate.of(2018, 9, 1),
            UNPROVED_CHILD, LocalDate.of(2020, 2, 2),
            FATHERS_CHILD, LocalDate.of(2016, 6, 6),
            TEEN, LocalDate.of(2011, 6, 20),
            ALMOST_THIRTEEN, LocalDate.of(2013, 3, 11));

    private static FamilyMember adult(UUID patient) {
        return new FamilyMember(patient, true, null, null, ADDED, true);
    }

    private static FamilyMember child(UUID patient, UUID requestedBy, boolean proved) {
        return new FamilyMember(patient, true, requestedBy, FamilyMember.Relation.MOTHER, ADDED, proved);
    }

    private static final FamilyGroup FAMILY = FamilyGroup.restore(UUID.randomUUID(), "Familia Díaz", ADDED, List.of(
            adult(MOTHER), adult(FATHER), child(CHILD, MOTHER, true), child(UNPROVED_CHILD, MOTHER, false),
            child(FATHERS_CHILD, FATHER, true), adult(TEEN), child(ALMOST_THIRTEEN, MOTHER, true)));

    private static FamilyPerson person(UUID patient) {
        return new FamilyPerson(patient, BIRTH_DATES.get(patient));
    }

    @Test
    void aParentAsksForTheirOwnAndForEachChildUnder13WhoseRelationWithThemIsProved() {
        assertThat(RecordCopyPolicy.askableFor(person(MOTHER), List.of(FAMILY), BIRTH_DATES, TODAY))
                .containsExactly(MOTHER, CHILD, ALMOST_THIRTEEN);
        assertThat(RecordCopyPolicy.askableFor(person(FATHER), List.of(FAMILY), BIRTH_DATES, TODAY))
                .containsExactly(FATHER, FATHERS_CHILD);
    }

    @Test
    void onTheirThirteenthBirthdayAChildsRecordIsTheirsAlone() {
        assertThat(RecordCopyPolicy.askableFor(person(MOTHER), List.of(FAMILY), BIRTH_DATES, TODAY.plusDays(1)))
                .containsExactly(MOTHER, CHILD);
        assertThat(RecordCopyPolicy.askableFor(person(TEEN), List.of(FAMILY), BIRTH_DATES, TODAY))
                .containsExactly(TEEN);
    }

    @Test
    void aChildAsksForNothingAndSoDoesSomeoneWithoutADateOfBirth() {
        assertThat(RecordCopyPolicy.askableFor(person(CHILD), List.of(FAMILY), BIRTH_DATES, TODAY)).isEmpty();
        assertThat(RecordCopyPolicy.askableFor(new FamilyPerson(MOTHER, null), List.of(FAMILY), BIRTH_DATES, TODAY))
                .containsExactly(CHILD, ALMOST_THIRTEEN);
    }

    /** A parent who left the group leaves the children in it (D-85): that group is no longer theirs. */
    @Test
    void aGroupTheParentLeftGivesNothingOfIt() {
        FamilyGroup left = FamilyGroup.restore(UUID.randomUUID(), "Familia Díaz", ADDED,
                List.of(adult(FATHER), child(CHILD, MOTHER, true)));

        assertThat(RecordCopyPolicy.askableFor(person(MOTHER), List.of(left), BIRTH_DATES, TODAY))
                .containsExactly(MOTHER);
    }

    @Test
    void aGroupThePatientIsNotInGivesNothing() {
        UUID stranger = UUID.randomUUID();
        assertThat(RecordCopyPolicy.askableFor(new FamilyPerson(stranger, LocalDate.of(1990, 1, 1)), List.of(FAMILY),
                BIRTH_DATES, TODAY)).containsExactly(stranger);
    }
}
