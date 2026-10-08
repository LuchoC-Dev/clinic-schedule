package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.ChildRequestsExceededException;
import com.clinic.schedule.portal.application.exception.FamilyGroupNotFoundException;
import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.application.port.AccountHolderPort;
import com.clinic.schedule.portal.application.port.AccountNamePort;
import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.portal.application.port.FamilyChangeRepository;
import com.clinic.schedule.portal.application.port.FamilyGroupRepository;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.application.port.RegistrantPort;
import com.clinic.schedule.portal.domain.AccountAction;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.domain.AgeBand;
import com.clinic.schedule.portal.domain.FamilyGroup;
import com.clinic.schedule.portal.domain.FamilyGroupService;
import com.clinic.schedule.portal.domain.FamilyMember;
import com.clinic.schedule.portal.domain.FamilyPerson;
import com.clinic.schedule.portal.domain.FamilyUpdate;
import com.clinic.schedule.portal.domain.PatientAccount;
import com.clinic.schedule.portal.domain.RegisteredChild;
import com.clinic.schedule.portal.domain.Registrant;
import com.clinic.schedule.portal.domain.WebRegistrationPolicy;
import com.clinic.schedule.portal.domain.exception.FamilyRefusedException;

/**
 * An adult registers a child under 13 on the web (D-92): with their own registration, as it is confirmed, or later
 * from their account, one at a time. The child is a patient registered on the web and unverified, and goes into a
 * family group with the adult, at their request, with the relation not proved until reception sees what proves it.
 *
 * <p>From the account the email is proven already, so the child is registered at once. A document the practice
 * knows registers nothing, and the answer is the same; whether the child appears among the adult's family tells
 * them, which nothing can hide, so each request is recorded in the account's history and at most
 * {@code app.portal.children-per-day} are taken in a day, those that registered nothing included.
 */
@Service
public class RegisterChildUseCase {

    private final CurrentPatientPort currentPatient;
    private final AccountHolderPort holders;
    private final PatientAccountRepository accounts;
    private final AccountChangeRepository accountChanges;
    private final AccountNamePort accountNames;
    private final RegistrantPort registrants;
    private final FamilyGroupRepository groups;
    private final FamilyChangeRepository familyChanges;
    private final Clock clock;
    private final int perDay;

    public RegisterChildUseCase(CurrentPatientPort currentPatient, AccountHolderPort holders,
            PatientAccountRepository accounts, AccountChangeRepository accountChanges, AccountNamePort accountNames,
            RegistrantPort registrants, FamilyGroupRepository groups, FamilyChangeRepository familyChanges,
            Clock clock, @Value("${app.portal.children-per-day}") int perDay) {
        this.currentPatient = currentPatient;
        this.holders = holders;
        this.accounts = accounts;
        this.accountChanges = accountChanges;
        this.accountNames = accountNames;
        this.registrants = registrants;
        this.groups = groups;
        this.familyChanges = familyChanges;
        this.clock = clock;
        this.perDay = perDay;
    }

    /**
     * The patient logged in registers a child of theirs, into a group of theirs or, without one, a new one.
     *
     * @throws com.clinic.schedule.portal.application.exception.InvalidRegistrationException when the name, the
     *         document or the date of birth cannot be right
     * @throws IllegalArgumentException when the date of birth or the relation is missing
     * @throws FamilyRefusedException when the patient is not an adult, the child is 13 or older, or the group is not
     *         one the patient is an adult of
     * @throws FamilyGroupNotFoundException when there is no such group
     * @throws ChildRequestsExceededException when the account asked for as many as a day takes
     */
    @Transactional
    public void handle(Command command) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        UUID me = currentPatient.patientId();
        AccountHolder adult = holders.find(me)
                .orElseThrow(() -> new IllegalStateException("No patient for the web account logged in"));
        if (adult.birthDate() == null || AgeBand.of(adult.birthDate(), today) != AgeBand.ADULT) {
            throw new FamilyRefusedException(FamilyRefusedException.Reason.NOT_AN_ADULT_MEMBER,
                    "Only an adult registers their children on the web");
        }
        RegisteredChild child = new RegisteredChild(new Registrant(strip(command.fullName()),
                strip(command.documentType()), strip(command.documentNumber()), command.birthDate(), null),
                command.relation());
        registrants.check(child.person());
        WebRegistrationPolicy.checkChild(child, today);
        FamilyGroup chosen = command.groupId() == null ? null : groups.findById(command.groupId())
                .orElseThrow(() -> new FamilyGroupNotFoundException(command.groupId()));
        if (chosen != null && chosen.member(me).isEmpty()) {
            throw new FamilyRefusedException(FamilyRefusedException.Reason.NOT_AN_ADULT_MEMBER,
                    "A child is added to a group of the adult's");
        }

        PatientAccount account = accounts.findByPatientId(me)
                .orElseThrow(() -> new IllegalStateException("No web account for the patient logged in"));
        if (accountChanges.countSince(account.id(), AccountAction.CHILD_REQUESTED, now.minusDays(1)) >= perDay) {
            throw new ChildRequestsExceededException(perDay);
        }
        accountChanges.save(account.childRequested(now));
        String by = accountNames.nameOf(account.id());
        Optional<UUID> childId = registrants.register(child.person());
        if (childId.isEmpty()) {
            return;
        }
        FamilyGroup group = chosen != null ? chosen : formed(adult, by, now);
        save(FamilyGroupService.addChildUnproved(group, new FamilyPerson(childId.get(), child.person().birthDate()),
                person(adult), child.relation(), now, by));
    }

    /**
     * The children an adult registered with their own registration, as it is confirmed: in a family group of theirs,
     * formed for them in the account's name.
     *
     * @param by the name of the adult's account
     * @return the children it could not register, by name: their document was someone's by then
     */
    @Transactional
    public List<String> registerWithAdult(AccountHolder adult, List<RegisteredChild> children, String by,
            LocalDateTime at) {
        List<String> notRegistered = new ArrayList<>();
        FamilyGroup group = null;
        for (RegisteredChild child : children) {
            Optional<UUID> childId = registrants.register(child.person());
            if (childId.isEmpty()) {
                notRegistered.add(child.fullName());
                continue;
            }
            if (group == null) {
                group = formed(adult, by, at);
            }
            group = save(FamilyGroupService.addChildUnproved(group,
                    new FamilyPerson(childId.get(), child.person().birthDate()), person(adult), child.relation(), at,
                    by));
        }
        return notRegistered;
    }

    /** A new group, named after the adult, with them in it sharing their appointments, as an adult who joins. */
    private FamilyGroup formed(AccountHolder adult, String by, LocalDateTime at) {
        String name = "Familia de " + adult.fullName();
        FamilyGroup group = save(FamilyGroupService.create(name.length() > 100 ? name.substring(0, 100) : name, at,
                by));
        return save(FamilyGroupService.join(group, person(adult), true, at, by));
    }

    private FamilyGroup save(FamilyUpdate update) {
        groups.save(update.group());
        familyChanges.save(update.change());
        return update.group();
    }

    private static FamilyPerson person(AccountHolder holder) {
        return new FamilyPerson(holder.patientId(), holder.birthDate());
    }

    private static String strip(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** A child under 13, and what the adult is to them; the group is one of the adult's, or null for a new one. */
    public record Command(String fullName, String documentType, String documentNumber, LocalDate birthDate,
            FamilyMember.Relation relation, UUID groupId) {
    }
}
