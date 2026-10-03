package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * For whom a patient acts on the web (D-85): from 18, the members of a group of theirs who share — a child
 * always —, never themselves; under 18, nobody else.
 */
class FamilyAccessPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 12, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private final Map<UUID, LocalDate> birthDates = new HashMap<>();

    private FamilyPerson person(LocalDate birthDate) {
        FamilyPerson person = new FamilyPerson(UUID.randomUUID(), birthDate);
        if (birthDate != null) {
            birthDates.put(person.patientId(), birthDate);
        }
        return person;
    }

    private static FamilyGroup join(FamilyGroup group, FamilyPerson person, boolean shares) {
        return FamilyGroupService.join(group, person, shares, NOW, "recepcion").group();
    }

    private static FamilyGroup group() {
        return FamilyGroupService.create("Familia", NOW, "recepcion").group();
    }

    @Test
    void anAdultActsForTheMembersWhoShareAndAChildAlways() {
        FamilyPerson mother = person(TODAY.minusYears(40));
        FamilyPerson partner = person(TODAY.minusYears(42));
        FamilyPerson sharingTeen = person(TODAY.minusYears(16));
        FamilyPerson privateTeen = person(TODAY.minusYears(14));
        FamilyPerson child = person(TODAY.minusYears(6));
        FamilyGroup group = join(join(join(join(group(), mother, true), partner, true), sharingTeen, true),
                privateTeen, false);
        group = FamilyGroupService.addChild(group, child, mother, FamilyMember.Relation.MOTHER, true, NOW, "r")
                .group();
        FamilyGroup privatePartner = FamilyGroupService.share(group, partner, false, NOW, "r").group();

        assertThat(FamilyAccessPolicy.actedFor(mother, List.of(group), birthDates, TODAY))
                .containsExactlyInAnyOrder(partner.patientId(), sharingTeen.patientId(), child.patientId());
        assertThat(FamilyAccessPolicy.actedFor(mother, List.of(privatePartner), birthDates, TODAY))
                .containsExactlyInAnyOrder(sharingTeen.patientId(), child.patientId());
    }

    @Test
    void whetherTheAdultSharesDoesNotMatter() {
        FamilyPerson father = person(TODAY.minusYears(40));
        FamilyPerson partner = person(TODAY.minusYears(38));
        FamilyGroup group = FamilyGroupService.share(join(join(group(), father, true), partner, true), father, false,
                NOW, "r").group();

        assertThat(FamilyAccessPolicy.actedFor(father, List.of(group), birthDates, TODAY))
                .containsExactly(partner.patientId());
    }

    @Test
    void underEighteenNobodyElseNorWithoutADateOfBirth() {
        FamilyPerson teen = person(TODAY.minusYears(18).plusDays(1));
        FamilyPerson adult = person(TODAY.minusYears(18));
        FamilyPerson mother = person(TODAY.minusYears(45));
        FamilyGroup group = join(join(join(group(), teen, true), adult, true), mother, true);

        assertThat(FamilyAccessPolicy.actedFor(teen, List.of(group), birthDates, TODAY)).isEmpty();
        assertThat(FamilyAccessPolicy.actedFor(adult, List.of(group), birthDates, TODAY))
                .containsExactlyInAnyOrder(teen.patientId(), mother.patientId());
        // A date of birth taken away after joining.
        FamilyPerson unknown = new FamilyPerson(mother.patientId(), null);
        assertThat(FamilyAccessPolicy.actedFor(unknown, List.of(group), birthDates, TODAY)).isEmpty();
    }

    @Test
    void aChildsAppointmentsTurnPrivateOnTheirThirteenthBirthday() {
        FamilyPerson mother = person(TODAY.minusYears(40));
        FamilyPerson child = person(LocalDate.of(2013, 3, 11));
        FamilyGroup group = FamilyGroupService.addChild(join(group(), mother, true), child, mother,
                FamilyMember.Relation.MOTHER, true, NOW, "r").group();

        assertThat(FamilyAccessPolicy.actedFor(mother, List.of(group), birthDates, TODAY))
                .containsExactly(child.patientId());
        assertThat(FamilyAccessPolicy.actedFor(mother, List.of(group), birthDates, TODAY.plusDays(1))).isEmpty();
    }

    @Test
    void onlyInTheGroupsTheyAreInAndAnyOfThem() {
        FamilyPerson mother = person(TODAY.minusYears(40));
        FamilyPerson grandmother = person(TODAY.minusYears(70));
        FamilyPerson stranger = person(TODAY.minusYears(30));
        FamilyGroup hers = join(join(group(), mother, true), grandmother, true);
        FamilyGroup notHers = join(group(), stranger, true);
        FamilyGroup grandmothers = FamilyGroupService.share(join(join(group(), grandmother, true), mother, true),
                grandmother, false, NOW, "r").group();

        assertThat(FamilyAccessPolicy.actedFor(mother, List.of(hers, notHers), birthDates, TODAY))
                .containsExactly(grandmother.patientId());
        // Private in one group of theirs, shared in another: the mother acts for her.
        assertThat(FamilyAccessPolicy.actedFor(mother, List.of(grandmothers, hers), birthDates, TODAY))
                .containsExactly(grandmother.patientId());
        assertThat(FamilyAccessPolicy.actedFor(mother, List.of(grandmothers), birthDates, TODAY)).isEmpty();
    }
}
