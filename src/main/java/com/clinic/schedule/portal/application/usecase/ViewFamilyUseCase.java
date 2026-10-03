package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.NotYourFamilyMemberException;
import com.clinic.schedule.portal.application.port.AccountHolderPort;
import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.portal.application.port.FamilyGroupRepository;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.domain.AgeBand;
import com.clinic.schedule.portal.domain.FamilyAccessPolicy;
import com.clinic.schedule.portal.domain.FamilyGroup;
import com.clinic.schedule.portal.domain.FamilyGroupService;
import com.clinic.schedule.portal.domain.FamilyMember;
import com.clinic.schedule.portal.domain.FamilyPerson;

/**
 * The family of the patient logged in (D-85): the groups they are in, as they read them, and the members they
 * act for — book, cancel and see the appointments of —, as {@link FamilyAccessPolicy} says. The other use cases
 * of the web ask it whom the patient acts for.
 */
@Service
public class ViewFamilyUseCase {

    private final CurrentPatientPort currentPatient;
    private final FamilyGroupRepository groups;
    private final AccountHolderPort patients;
    private final Clock clock;

    public ViewFamilyUseCase(CurrentPatientPort currentPatient, FamilyGroupRepository groups,
            AccountHolderPort patients, Clock clock) {
        this.currentPatient = currentPatient;
        this.groups = groups;
        this.patients = patients;
        this.clock = clock;
    }

    /** The patient's groups, by name, each with its members: the patient first, then by name. */
    @Transactional(readOnly = true)
    public List<Group> groups() {
        UUID me = currentPatient.patientId();
        List<FamilyGroup> mine = groups.findOf(me);
        Map<UUID, AccountHolder> people = people(mine);
        LocalDate today = LocalDate.now(clock);
        return mine.stream()
                .map(group -> new Group(group.id(), group.name(), group.members().stream()
                        .map(member -> member(member, people.get(member.patientId()), me, today))
                        .sorted(Comparator.comparing((Member member) -> !member.me())
                                .thenComparing(Member::fullName))
                        .toList()))
                .toList();
    }

    /** Whom the patient acts for besides themselves, by name. */
    @Transactional(readOnly = true)
    public List<AccountHolder> actedFor() {
        UUID me = currentPatient.patientId();
        List<FamilyGroup> mine = groups.findOf(me);
        Map<UUID, AccountHolder> people = people(mine);
        Map<UUID, LocalDate> birthDates = people.values().stream()
                .filter(person -> person.birthDate() != null)
                .collect(Collectors.toMap(AccountHolder::patientId, AccountHolder::birthDate));
        Set<UUID> members = FamilyAccessPolicy.actedFor(new FamilyPerson(me, birthDates.get(me)), mine,
                birthDates, LocalDate.now(clock));
        return members.stream()
                .map(people::get)
                .sorted(Comparator.comparing(AccountHolder::fullName))
                .toList();
    }

    /**
     * The patient themselves, then whom they act for: whom they may book for.
     */
    @Transactional(readOnly = true)
    public List<AccountHolder> bookableFor() {
        List<AccountHolder> whom = new ArrayList<>();
        UUID me = currentPatient.patientId();
        whom.add(patients.find(me).orElseThrow(() -> new IllegalStateException("No patient for the account")));
        whom.addAll(actedFor());
        return whom;
    }

    /**
     * @return the member, when the patient acts for them
     * @throws NotYourFamilyMemberException when they do not
     */
    @Transactional(readOnly = true)
    public AccountHolder requireActedFor(UUID patientId) {
        return actedFor().stream()
                .filter(member -> member.patientId().equals(patientId))
                .findFirst()
                .orElseThrow(() -> new NotYourFamilyMemberException(patientId));
    }

    private Map<UUID, AccountHolder> people(List<FamilyGroup> mine) {
        Set<UUID> ids = new HashSet<>();
        mine.forEach(group -> group.members().forEach(member -> ids.add(member.patientId())));
        return patients.findAll(ids).stream().collect(Collectors.toMap(AccountHolder::patientId,
                Function.identity()));
    }

    private static Member member(FamilyMember member, AccountHolder person, UUID me, LocalDate today) {
        return new Member(person.fullName(),
                person.birthDate() == null ? null : AgeBand.of(person.birthDate(), today),
                FamilyGroupService.sharesOn(member, new FamilyPerson(member.patientId(), person.birthDate()), today),
                Objects.equals(member.patientId(), me));
    }

    /** A group of the patient's, as they read it. */
    public record Group(UUID id, String name, List<Member> members) {
    }

    /**
     * A member as the others read them: their name, how old as the group's rules count it — null without a date of
     * birth —, and whether the group sees their appointments. Nothing else of theirs.
     */
    public record Member(String fullName, AgeBand age, boolean shares, boolean me) {
    }
}
