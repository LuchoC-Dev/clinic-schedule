package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.FamilyGroupNotFoundException;
import com.clinic.schedule.portal.application.exception.UnknownPatientException;
import com.clinic.schedule.portal.application.port.AccountHolderPort;
import com.clinic.schedule.portal.application.port.CurrentUserPort;
import com.clinic.schedule.portal.application.port.FamilyChangeRepository;
import com.clinic.schedule.portal.application.port.FamilyGroupRepository;
import com.clinic.schedule.portal.domain.FamilyGroup;
import com.clinic.schedule.portal.domain.FamilyGroupService;
import com.clinic.schedule.portal.domain.FamilyMember;
import com.clinic.schedule.portal.domain.FamilyPerson;
import com.clinic.schedule.portal.domain.FamilyUpdate;

/**
 * Reception forms the family groups at the desk (D-84): creates one, lets in whoever of 13 or older is present
 * and agrees, adds a child at the request of their parent or guardian in the group, takes members out, and
 * records whether a member of 13 or older shares their appointments. {@link FamilyGroupService} says what may be
 * done; this fetches the patients' dates of birth and saves each change with its entry, by reception's user.
 */
@Service
public class ManageFamilyGroupUseCase {

    private final FamilyGroupRepository groups;
    private final FamilyChangeRepository changes;
    private final AccountHolderPort patients;
    private final CurrentUserPort currentUser;
    private final Clock clock;

    public ManageFamilyGroupUseCase(FamilyGroupRepository groups, FamilyChangeRepository changes,
            AccountHolderPort patients, CurrentUserPort currentUser, Clock clock) {
        this.groups = groups;
        this.changes = changes;
        this.patients = patients;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** @throws IllegalArgumentException when the name is blank or too long */
    @Transactional
    public FamilyGroup create(String name) {
        return save(FamilyGroupService.create(name, LocalDateTime.now(clock), currentUser.username()));
    }

    /**
     * @param shares whether an adolescent chooses to share their appointments now
     * @throws com.clinic.schedule.portal.domain.exception.FamilyRefusedException when they may not join
     */
    @Transactional
    public FamilyGroup join(UUID groupId, UUID patientId, boolean shares) {
        return save(FamilyGroupService.join(group(groupId), person(patientId), shares, LocalDateTime.now(clock),
                currentUser.username()));
    }

    /** @throws com.clinic.schedule.portal.domain.exception.FamilyRefusedException when the child may not be added */
    @Transactional
    public FamilyGroup addChild(UUID groupId, UUID childId, UUID requesterId, FamilyMember.Relation relation,
            boolean proofSeen) {
        return save(FamilyGroupService.addChild(group(groupId), person(childId), person(requesterId), relation,
                proofSeen, LocalDateTime.now(clock), currentUser.username()));
    }

    /**
     * Reception saw what proves the relation of a child an adult registered on the web (D-92).
     *
     * @throws com.clinic.schedule.portal.domain.exception.FamilyRefusedException when there is nothing to prove, or
     *         it was not seen
     */
    @Transactional
    public FamilyGroup proveRelation(UUID groupId, UUID patientId, boolean proofSeen) {
        return save(FamilyGroupService.proveRelation(group(groupId), patientId, proofSeen, LocalDateTime.now(clock),
                currentUser.username()));
    }

    /** @throws com.clinic.schedule.portal.domain.exception.FamilyRefusedException when they are not in it */
    @Transactional
    public FamilyGroup remove(UUID groupId, UUID patientId) {
        return save(FamilyGroupService.remove(group(groupId), patientId, LocalDateTime.now(clock),
                currentUser.username()));
    }

    /** @throws com.clinic.schedule.portal.domain.exception.FamilyRefusedException for a child, who shares always */
    @Transactional
    public FamilyGroup share(UUID groupId, UUID patientId, boolean shares) {
        return save(FamilyGroupService.share(group(groupId), person(patientId), shares, LocalDateTime.now(clock),
                currentUser.username()));
    }

    private FamilyGroup save(FamilyUpdate update) {
        groups.save(update.group());
        changes.save(update.change());
        return update.group();
    }

    private FamilyGroup group(UUID groupId) {
        return groups.findById(groupId).orElseThrow(() -> new FamilyGroupNotFoundException(groupId));
    }

    private FamilyPerson person(UUID patientId) {
        return patients.find(patientId)
                .map(holder -> new FamilyPerson(holder.patientId(), holder.birthDate()))
                .orElseThrow(() -> new UnknownPatientException(patientId));
    }
}
