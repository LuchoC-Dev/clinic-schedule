package com.clinic.schedule.portal.application.usecase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.AccountNotFoundException;
import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.domain.AccountChange;
import com.clinic.schedule.portal.domain.PatientAccount;

/**
 * Web accounts as reception reads them, with their history (D-79), and as other modules ask about them. Who
 * may read them is the route's to say: reception alone (D-34).
 */
@Service
public class FindAccountUseCase {

    private final PatientAccountRepository accounts;
    private final AccountChangeRepository changes;

    public FindAccountUseCase(PatientAccountRepository accounts, AccountChangeRepository changes) {
        this.accounts = accounts;
        this.changes = changes;
    }

    /** The patient's account, in any state, if they have one. */
    @Transactional(readOnly = true)
    public Optional<PatientAccount> byPatient(UUID patientId) {
        return accounts.findByPatientId(patientId);
    }

    /** @throws AccountNotFoundException when there is no such account */
    @Transactional(readOnly = true)
    public PatientAccount byId(UUID accountId) {
        return accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    /**
     * What was done to the account, the most recent first.
     *
     * @throws AccountNotFoundException when there is no such account
     */
    @Transactional(readOnly = true)
    public List<AccountChange> history(UUID accountId) {
        byId(accountId);
        return changes.findForAccount(accountId);
    }
}
