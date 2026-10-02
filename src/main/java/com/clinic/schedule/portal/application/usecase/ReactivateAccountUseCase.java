package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.AccountNotFoundException;
import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.application.port.CurrentUserPort;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.domain.AccountUpdate;
import com.clinic.schedule.portal.domain.PatientAccount;

/**
 * Reception switches a web account back on (D-79): in use again with the password the patient had chosen,
 * or waiting to be activated if they never chose one — reception then sends the link again.
 */
@Service
public class ReactivateAccountUseCase {

    private final PatientAccountRepository accounts;
    private final AccountChangeRepository changes;
    private final CurrentUserPort currentUser;
    private final Clock clock;

    public ReactivateAccountUseCase(PatientAccountRepository accounts, AccountChangeRepository changes,
            CurrentUserPort currentUser, Clock clock) {
        this.accounts = accounts;
        this.changes = changes;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * @throws AccountNotFoundException when there is no such account
     * @throws com.clinic.schedule.portal.domain.exception.AccountStateException unless it is inactive
     */
    @Transactional
    public PatientAccount handle(UUID accountId) {
        PatientAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        AccountUpdate reactivated = account.reactivate(LocalDateTime.now(clock), currentUser.username());
        accounts.save(reactivated.account());
        changes.save(reactivated.change());
        return reactivated.account();
    }
}
