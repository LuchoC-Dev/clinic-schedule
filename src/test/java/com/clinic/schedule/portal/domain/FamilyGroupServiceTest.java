package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.exception.FamilyRefusedException;
import com.clinic.schedule.portal.domain.exception.FamilyRefusedException.Reason;

/**
 * Who joins a family group and how (D-84): from 13 by themselves, a child at a parent's request with the proof
 * seen, and whose appointments the group sees.
 */
class FamilyGroupServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 12, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private static final FamilyPerson ADULT = person(TODAY.minusYears(40));
    private static final FamilyPerson ADOLESCENT = person(TODAY.minusYears(15));
    private static final FamilyPerson CHILD = person(TODAY.minusYears(8));

    private static FamilyPerson person(LocalDate birthDate) {
        return new FamilyPerson(UUID.randomUUID(), birthDate);
    }

    private static FamilyGroup empty() {
        return FamilyGroupService.create("Familia Pérez", NOW, "recepcion").group();
    }

    private static FamilyGroup withAdult() {
        return FamilyGroupService.join(empty(), ADULT, false, NOW, "recepcion").group();
    }

    private static Reason refusal(Runnable action) {
        try {
            action.run();
        } catch (FamilyRefusedException e) {
            return e.reason();
        }
        return null;
    }

    @Test
    void creatingIsRecordedWithNoMember() {
        FamilyUpdate created = FamilyGroupService.create("  Familia Pérez ", NOW, "recepcion");

        assertThat(created.group().name()).isEqualTo("Familia Pérez");
        assertThat(created.group().members()).isEmpty();
        assertThat(created.change().action()).isEqualTo(FamilyChange.Action.CREATED);
        assertThat(created.change().patientId()).isNull();
        assertThat(created.change().groupId()).isEqualTo(created.group().id());
        assertThat(created.change().by()).isEqualTo("recepcion");
        assertThatThrownBy(() -> FamilyGroupService.create(" ", NOW, "recepcion"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FamilyGroupService.create("x".repeat(101), NOW, "recepcion"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(FamilyGroupService.create("x".repeat(100), NOW, "recepcion").group().name()).hasSize(100);
    }

    @Test
    void anAdultWhoJoinsShares() {
        FamilyUpdate joined = FamilyGroupService.join(empty(), ADULT, false, NOW, "recepcion");

        FamilyMember member = joined.group().member(ADULT.patientId()).orElseThrow();
        assertThat(member.shares()).isTrue();
        assertThat(member.requestedBy()).isNull();
        assertThat(member.addedAt()).isEqualTo(NOW);
        assertThat(joined.change().action()).isEqualTo(FamilyChange.Action.JOINED);
        assertThat(joined.change().patientId()).isEqualTo(ADULT.patientId());
    }

    @Test
    void anAdolescentChoosesWhetherToShare() {
        assertThat(FamilyGroupService.join(empty(), ADOLESCENT, false, NOW, "recepcion").group()
                .member(ADOLESCENT.patientId()).orElseThrow().shares()).isFalse();
        assertThat(FamilyGroupService.join(empty(), ADOLESCENT, true, NOW, "recepcion").group()
                .member(ADOLESCENT.patientId()).orElseThrow().shares()).isTrue();
    }

    @Test
    void aChildDoesNotJoinByThemselves() {
        assertThat(refusal(() -> FamilyGroupService.join(withAdult(), CHILD, true, NOW, "recepcion")))
                .isEqualTo(Reason.A_CHILD);
        assertThat(refusal(() -> FamilyGroupService.join(withAdult(), person(null), true, NOW, "recepcion")))
                .isEqualTo(Reason.NO_BIRTH_DATE);
        assertThat(refusal(() -> FamilyGroupService.join(withAdult(), ADULT, true, NOW, "recepcion")))
                .isEqualTo(Reason.ALREADY_A_MEMBER);
    }

    @Test
    void aChildIsAddedAtTheRequestOfAnAdultMemberOnceTheProofIsSeen() {
        FamilyUpdate added = FamilyGroupService.addChild(withAdult(), CHILD, ADULT, FamilyMember.Relation.MOTHER,
                true, NOW, "recepcion");

        FamilyMember child = added.group().member(CHILD.patientId()).orElseThrow();
        assertThat(child.requestedBy()).isEqualTo(ADULT.patientId());
        assertThat(child.relation()).isEqualTo(FamilyMember.Relation.MOTHER);
        assertThat(child.shares()).isFalse();
        assertThat(added.change().action()).isEqualTo(FamilyChange.Action.CHILD_ADDED);
        assertThat(added.change().patientId()).isEqualTo(CHILD.patientId());
        assertThat(added.group().members()).hasSize(2);
    }

    @Test
    void aChildIsRefusedWithoutAnyOfThat() {
        FamilyGroup group = withAdult();
        FamilyMember.Relation mother = FamilyMember.Relation.MOTHER;

        assertThat(refusal(() -> FamilyGroupService.addChild(group, ADOLESCENT, ADULT, mother, true, NOW, "r")))
                .isEqualTo(Reason.NOT_A_CHILD);
        assertThat(refusal(() -> FamilyGroupService.addChild(group, person(null), ADULT, mother, true, NOW, "r")))
                .isEqualTo(Reason.NO_BIRTH_DATE);
        assertThat(refusal(() -> FamilyGroupService.addChild(group, CHILD, person(TODAY.minusYears(40)), mother,
                true, NOW, "r"))).isEqualTo(Reason.NOT_AN_ADULT_MEMBER);
        FamilyGroup withAdolescent = FamilyGroupService.join(group, ADOLESCENT, true, NOW, "r").group();
        assertThat(refusal(() -> FamilyGroupService.addChild(withAdolescent, CHILD, ADOLESCENT, mother, true, NOW,
                "r"))).isEqualTo(Reason.NOT_AN_ADULT_MEMBER);
        assertThat(refusal(() -> FamilyGroupService.addChild(group, CHILD, ADULT, mother, false, NOW, "r")))
                .isEqualTo(Reason.NO_PROOF);
        assertThat(refusal(() -> FamilyGroupService.addChild(group, CHILD, ADULT, null, true, NOW, "r")))
                .isEqualTo(Reason.NO_PROOF);
        FamilyGroup withChild = FamilyGroupService.addChild(group, CHILD, ADULT, mother, true, NOW, "r").group();
        assertThat(refusal(() -> FamilyGroupService.addChild(withChild, CHILD, ADULT, mother, true, NOW, "r")))
                .isEqualTo(Reason.ALREADY_A_MEMBER);
    }

    @Test
    void removingLeavesTheRest() {
        FamilyGroup group = FamilyGroupService.addChild(withAdult(), CHILD, ADULT, FamilyMember.Relation.FATHER,
                true, NOW, "r").group();

        FamilyUpdate removed = FamilyGroupService.remove(group, ADULT.patientId(), NOW, "recepcion");

        assertThat(removed.group().members()).extracting(FamilyMember::patientId).containsExactly(CHILD.patientId());
        assertThat(removed.change().action()).isEqualTo(FamilyChange.Action.REMOVED);
        assertThat(removed.change().patientId()).isEqualTo(ADULT.patientId());
        assertThat(refusal(() -> FamilyGroupService.remove(removed.group(), ADULT.patientId(), NOW, "r")))
                .isEqualTo(Reason.NOT_A_MEMBER);
    }

    @Test
    void fromThirteenEachMemberChoosesWhetherTheGroupSeesTheirAppointments() {
        FamilyGroup group = FamilyGroupService.join(withAdult(), ADOLESCENT, true, NOW, "r").group();

        FamilyUpdate stopped = FamilyGroupService.share(group, ADULT, false, NOW, "recepcion");
        assertThat(stopped.group().member(ADULT.patientId()).orElseThrow().shares()).isFalse();
        assertThat(stopped.group().member(ADOLESCENT.patientId()).orElseThrow().shares()).isTrue();
        assertThat(stopped.change().action()).isEqualTo(FamilyChange.Action.SHARING_STOPPED);
        assertThat(stopped.change().patientId()).isEqualTo(ADULT.patientId());

        FamilyUpdate started = FamilyGroupService.share(stopped.group(), ADULT, true, NOW, "recepcion");
        assertThat(started.group().member(ADULT.patientId()).orElseThrow().shares()).isTrue();
        assertThat(started.change().action()).isEqualTo(FamilyChange.Action.SHARING_STARTED);
    }

    @Test
    void aChildCannotStopSharingNorSomeoneOutOfTheGroupChoose() {
        FamilyGroup group = FamilyGroupService.addChild(withAdult(), CHILD, ADULT, FamilyMember.Relation.GUARDIAN,
                true, NOW, "r").group();

        assertThat(refusal(() -> FamilyGroupService.share(group, CHILD, false, NOW, "r")))
                .isEqualTo(Reason.CHILDREN_SHARE);
        assertThat(refusal(() -> FamilyGroupService.share(group, ADOLESCENT, true, NOW, "r")))
                .isEqualTo(Reason.NOT_A_MEMBER);
    }

    @Test
    void theGroupSeesAChildsAppointmentsUntilTheyTurnThirteen() {
        LocalDate born = LocalDate.of(2013, 6, 15);
        FamilyMember child = new FamilyMember(UUID.randomUUID(), false, UUID.randomUUID(),
                FamilyMember.Relation.MOTHER, NOW);
        FamilyPerson person = new FamilyPerson(child.patientId(), born);

        assertThat(FamilyGroupService.sharesOn(child, person, LocalDate.of(2026, 6, 14))).isTrue();
        assertThat(FamilyGroupService.sharesOn(child, person, LocalDate.of(2026, 6, 15))).isFalse();
        FamilyMember sharing = new FamilyMember(child.patientId(), true, null, null, NOW);
        assertThat(FamilyGroupService.sharesOn(sharing, person, LocalDate.of(2026, 6, 15))).isTrue();
        assertThat(FamilyGroupService.sharesOn(sharing, new FamilyPerson(child.patientId(), null), TODAY)).isTrue();
        assertThat(FamilyGroupService.sharesOn(child, new FamilyPerson(child.patientId(), null), TODAY)).isFalse();
    }

    @Test
    void aGroupHoldsAPatientOnce() {
        UUID patient = UUID.randomUUID();
        List<FamilyMember> twice = List.of(new FamilyMember(patient, true, null, null, NOW),
                new FamilyMember(patient, false, null, null, NOW));

        assertThatThrownBy(() -> FamilyGroup.restore(UUID.randomUUID(), "Familia", NOW, twice))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FamilyMember(patient, false, UUID.randomUUID(), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aChangeIsAboutAMemberButTheGroupsCreation() {
        UUID group = UUID.randomUUID();

        assertThatThrownBy(() -> new FamilyChange(UUID.randomUUID(), group, FamilyChange.Action.CREATED,
                UUID.randomUUID(), NOW, "recepcion")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FamilyChange(UUID.randomUUID(), group, FamilyChange.Action.JOINED, null, NOW,
                "recepcion")).isInstanceOf(IllegalArgumentException.class);
    }
}
