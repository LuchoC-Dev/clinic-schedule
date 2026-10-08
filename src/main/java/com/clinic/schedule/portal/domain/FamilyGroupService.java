package com.clinic.schedule.portal.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.domain.exception.FamilyRefusedException;
import com.clinic.schedule.portal.domain.exception.FamilyRefusedException.Reason;

/**
 * The only way to change a family group, with the rules that need its members' ages (D-17, D-84):
 *
 * <ul>
 *   <li>whoever is 13 or older joins by themselves, present at the desk and agreeing. An adult's agreeing is
 *       sharing their appointments; an adolescent's appointments stay private unless they choose to share
 *       them (Código Civil y Comercial, art. 26; Ley 26.529, art. 2 d);</li>
 *   <li>a child under 13 is added at the request of an adult of the group who is their parent or guardian,
 *       once reception has seen what proves it; a child shares always, since their parents act for them;</li>
 *   <li>from 13, each member chooses whether the group sees their appointments;</li>
 *   <li>nobody joins without a date of birth, since nothing could tell which of these applies.</li>
 * </ul>
 *
 * <p>A child an adult registers on the web is added at their request with the relation not proved (D-92), and
 * reception proves it at the desk afterwards.
 *
 * <p>It is handed the patients' dates of birth and reads nothing itself.
 */
public final class FamilyGroupService {

    private FamilyGroupService() {
    }

    public static FamilyUpdate create(String name, LocalDateTime at, String by) {
        return FamilyGroup.create(name, at, by);
    }

    /**
     * Someone of 13 or older joins, present and agreeing.
     *
     * @param shares whether an adolescent chooses to share their appointments; an adult's agreeing is sharing
     * @throws FamilyRefusedException when they are a child, have no date of birth, or are in the group already
     */
    public static FamilyUpdate join(FamilyGroup group, FamilyPerson person, boolean shares, LocalDateTime at,
            String by) {
        AgeBand age = ageOf(person, at.toLocalDate());
        if (age == AgeBand.CHILD) {
            throw new FamilyRefusedException(Reason.A_CHILD, "A child under 13 is added at a parent's request");
        }
        requireNotAMember(group, person.patientId());
        return group.with(new FamilyMember(person.patientId(), age == AgeBand.ADULT || shares, null, null, at, true),
                FamilyChange.Action.JOINED, at, by);
    }

    /**
     * A child under 13 is added at the request of a member who is their parent or guardian.
     *
     * @param requester the adult of the group who asks for it
     * @param proofSeen whether reception saw what proves the requester is the child's parent or guardian
     * @throws FamilyRefusedException when any of that does not hold
     */
    public static FamilyUpdate addChild(FamilyGroup group, FamilyPerson child, FamilyPerson requester,
            FamilyMember.Relation relation, boolean proofSeen, LocalDateTime at, String by) {
        requireChildOf(group, child, requester, at.toLocalDate());
        if (relation == null || !proofSeen) {
            throw new FamilyRefusedException(Reason.NO_PROOF,
                    "A child is added once reception has seen who their parent or guardian is");
        }
        return group.with(new FamilyMember(child.patientId(), false, requester.patientId(), relation, at, true),
                FamilyChange.Action.CHILD_ADDED, at, by);
    }

    /**
     * A child under 13 an adult registered on the web, added at their request with the relation they named and not
     * proved, until reception sees what proves it (D-92).
     *
     * @param requester the adult of the group who registered them
     * @param by the account of the adult
     * @throws FamilyRefusedException as {@link #addChild}, but for the proof
     */
    public static FamilyUpdate addChildUnproved(FamilyGroup group, FamilyPerson child, FamilyPerson requester,
            FamilyMember.Relation relation, LocalDateTime at, String by) {
        requireChildOf(group, child, requester, at.toLocalDate());
        if (relation == null) {
            throw new FamilyRefusedException(Reason.NO_PROOF, "A child is added with who their parent or guardian is");
        }
        return group.with(new FamilyMember(child.patientId(), false, requester.patientId(), relation, at, false),
                FamilyChange.Action.CHILD_ADDED, at, by);
    }

    /**
     * Reception saw what proves the relation of a child an adult registered on the web (D-92): from then on the
     * child is in the group as any other.
     *
     * @throws FamilyRefusedException when they are not in the group, it is not seen, or there is nothing to prove
     */
    public static FamilyUpdate proveRelation(FamilyGroup group, UUID patientId, boolean proofSeen, LocalDateTime at,
            String by) {
        requireAMember(group, patientId);
        if (group.member(patientId).orElseThrow().relationProved()) {
            throw new FamilyRefusedException(Reason.NOTHING_TO_PROVE, "There is no relation to prove for that member");
        }
        if (!proofSeen) {
            throw new FamilyRefusedException(Reason.NO_PROOF,
                    "A relation is proved once reception has seen what proves it");
        }
        return group.proving(patientId, at, by);
    }

    /** A child under 13, not in the group, asked for by an adult of it. */
    private static void requireChildOf(FamilyGroup group, FamilyPerson child, FamilyPerson requester,
            LocalDate today) {
        if (ageOf(child, today) != AgeBand.CHILD) {
            throw new FamilyRefusedException(Reason.NOT_A_CHILD, "Whoever is 13 or older joins by themselves");
        }
        requireNotAMember(group, child.patientId());
        if (group.member(requester.patientId()).isEmpty() || ageOf(requester, today) != AgeBand.ADULT) {
            throw new FamilyRefusedException(Reason.NOT_AN_ADULT_MEMBER,
                    "A child is added at the request of an adult of the group");
        }
    }

    /** @throws FamilyRefusedException when the patient is not in the group */
    public static FamilyUpdate remove(FamilyGroup group, UUID patientId, LocalDateTime at, String by) {
        requireAMember(group, patientId);
        return group.without(patientId, at, by);
    }

    /**
     * A member of 13 or older chooses whether the group sees their appointments.
     *
     * @throws FamilyRefusedException when they are not in the group, or are a child, who shares always
     */
    public static FamilyUpdate share(FamilyGroup group, FamilyPerson person, boolean shares, LocalDateTime at,
            String by) {
        requireAMember(group, person.patientId());
        if (ageOf(person, at.toLocalDate()) == AgeBand.CHILD) {
            throw new FamilyRefusedException(Reason.CHILDREN_SHARE, "A child under 13 shares always");
        }
        return group.sharing(person.patientId(), shares, at, by);
    }

    /** Whether the group sees the member's appointments: a child's always, anyone else's by their choice. */
    public static boolean sharesOn(FamilyMember member, FamilyPerson person, LocalDate today) {
        return person.birthDate() == null ? member.shares()
                : AgeBand.of(person.birthDate(), today) == AgeBand.CHILD || member.shares();
    }

    private static AgeBand ageOf(FamilyPerson person, LocalDate today) {
        if (person.birthDate() == null) {
            throw new FamilyRefusedException(Reason.NO_BIRTH_DATE, "A member of a family group needs a date of birth");
        }
        return AgeBand.of(person.birthDate(), today);
    }

    private static void requireNotAMember(FamilyGroup group, UUID patientId) {
        if (group.member(patientId).isPresent()) {
            throw new FamilyRefusedException(Reason.ALREADY_A_MEMBER, "The patient is in the group already");
        }
    }

    private static void requireAMember(FamilyGroup group, UUID patientId) {
        if (group.member(patientId).isEmpty()) {
            throw new FamilyRefusedException(Reason.NOT_A_MEMBER, "The patient is not in the group");
        }
    }
}
