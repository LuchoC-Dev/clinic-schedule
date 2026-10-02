package com.clinic.schedule.portal.adapter.in.external;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.portal.api.LoginAccount;
import com.clinic.schedule.portal.api.PatientAccountReader;
import com.clinic.schedule.portal.application.usecase.FindAccountUseCase;
import com.clinic.schedule.portal.domain.AccountStatus;

/**
 * Answers this module's {@link PatientAccountReader} for other modules, as the appointment module answers
 * its reader (D-48): it calls a use case and translates the answer into the api's own types.
 *
 * <p>It reaches nothing of the patient module, which asks it whether a patient has an account: depending on
 * it here would close a cycle.
 */
@Component
class PatientAccountReaderAdapter implements PatientAccountReader {

    private final FindAccountUseCase accounts;

    PatientAccountReaderAdapter(FindAccountUseCase accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean hasAccount(UUID patientId) {
        return accounts.byPatient(patientId).isPresent();
    }

    @Override
    public Optional<LoginAccount> findActiveForLogin(UUID patientId) {
        return accounts.byPatient(patientId)
                .filter(account -> account.status() == AccountStatus.ACTIVE)
                .map(account -> new LoginAccount(account.id(), account.patientId(), account.passwordHash()));
    }
}
