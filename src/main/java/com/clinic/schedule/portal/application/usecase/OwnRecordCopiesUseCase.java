package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.NotYourRecordException;
import com.clinic.schedule.portal.application.port.AccountHolderPort;
import com.clinic.schedule.portal.application.port.AccountNamePort;
import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.portal.application.port.FamilyGroupRepository;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.application.port.RecordCopyPort;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.domain.FamilyGroup;
import com.clinic.schedule.portal.domain.FamilyPerson;
import com.clinic.schedule.portal.domain.RecordCopyPolicy;
import com.clinic.schedule.portal.domain.RecordCopyRequest;

/**
 * The patient logged in asks for a copy of a clinical record, follows it, and downloads it (D-95): their own from 13,
 * or their child's under 13 once reception proved the relation, as {@link RecordCopyPolicy} says at each request.
 * Anyone else's is refused with a 403 (D-50). The clinical record module keeps the request, puts the copy together
 * and has it authenticated; downloading it is a reading of the record, in the account's name (D-81).
 */
@Service
public class OwnRecordCopiesUseCase {

    private final CurrentPatientPort currentPatient;
    private final FamilyGroupRepository groups;
    private final AccountHolderPort patients;
    private final PatientAccountRepository accounts;
    private final AccountNamePort accountNames;
    private final RecordCopyPort copies;
    private final Clock clock;

    public OwnRecordCopiesUseCase(CurrentPatientPort currentPatient, FamilyGroupRepository groups,
            AccountHolderPort patients, PatientAccountRepository accounts, AccountNamePort accountNames,
            RecordCopyPort copies, Clock clock) {
        this.currentPatient = currentPatient;
        this.groups = groups;
        this.patients = patients;
        this.accounts = accounts;
        this.accountNames = accountNames;
        this.copies = copies;
        this.clock = clock;
    }

    /** Whose record the patient may ask a copy of: themselves first, when they may, then their children. */
    @Transactional(readOnly = true)
    public List<AccountHolder> askableFor() {
        Map<UUID, AccountHolder> people = new LinkedHashMap<>();
        Set<UUID> whom = whom(people);
        return whom.stream().map(people::get).toList();
    }

    /** The requests of every record the patient may ask about, the most recent first, each with whose it is. */
    @Transactional(readOnly = true)
    public List<Entry> list() {
        Map<UUID, AccountHolder> people = new LinkedHashMap<>();
        Set<UUID> whom = whom(people);
        return copies.findForPatients(whom).stream()
                .map(copy -> new Entry(copy, people.get(copy.patientId()),
                        copy.patientId().equals(currentPatient.patientId())))
                .toList();
    }

    /**
     * @param patientId whose record; null for the patient's own
     * @throws NotYourRecordException when it is not one they may ask a copy of
     * @throws com.clinic.schedule.portal.application.exception.RecordCopyWaitingException when it has a copy waiting
     */
    @Transactional
    public RecordCopyRequest request(UUID patientId, RecordCopyRequest.Delivery delivery) {
        UUID me = currentPatient.patientId();
        UUID whose = patientId == null ? me : patientId;
        if (!whom(new LinkedHashMap<>()).contains(whose)) {
            throw new NotYourRecordException(whose);
        }
        if (delivery == null) {
            throw new IllegalArgumentException("Say how the copy is to be delivered");
        }
        return copies.request(whose, me, accountName(me), delivery);
    }

    /**
     * @throws NotYourRecordException when it is not a copy of a record they may ask about, or there is none
     * @throws com.clinic.schedule.portal.application.exception.RecordCopyNotReadyException when it goes on paper, or
     *         is not authenticated yet
     */
    @Transactional
    public byte[] download(UUID copyId) {
        UUID me = currentPatient.patientId();
        return copies.downloadPdf(copyId, whom(new LinkedHashMap<>()), accountName(me))
                .orElseThrow(() -> new NotYourRecordException(copyId));
    }

    private String accountName(UUID me) {
        return accounts.findByPatientId(me)
                .map(account -> accountNames.nameOf(account.id()))
                .orElseThrow(() -> new IllegalStateException("No account for the patient logged in"));
    }

    /** Works out whom the patient may ask for, filling {@code people} with them. */
    private Set<UUID> whom(Map<UUID, AccountHolder> people) {
        UUID me = currentPatient.patientId();
        List<FamilyGroup> mine = groups.findOf(me);
        Set<UUID> ids = new HashSet<>();
        ids.add(me);
        mine.forEach(group -> group.members().forEach(member -> ids.add(member.patientId())));
        Map<UUID, AccountHolder> found = patients.findAll(ids).stream()
                .collect(Collectors.toMap(AccountHolder::patientId, Function.identity()));
        Map<UUID, LocalDate> birthDates = found.values().stream()
                .filter(person -> person.birthDate() != null)
                .collect(Collectors.toMap(AccountHolder::patientId, AccountHolder::birthDate));
        Set<UUID> whom = RecordCopyPolicy.askableFor(new FamilyPerson(me, birthDates.get(me)), mine, birthDates,
                LocalDate.now(clock));
        whom.forEach(id -> people.put(id, found.get(id)));
        return whom;
    }

    /** A request, whose record it is, and whether that is the patient's own. */
    public record Entry(RecordCopyRequest copy, AccountHolder patient, boolean own) {
    }
}
