package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.api.PatientAccountDeactivated;
import com.clinic.schedule.portal.application.exception.AccountNotFoundException;
import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.application.port.ActivationTokenRepository;
import com.clinic.schedule.portal.application.port.CurrentUserPort;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.domain.AccountUpdate;
import com.clinic.schedule.portal.domain.PatientAccount;

/**
 * Reception switches a web account off (D-79). It loses access at once: a pending activation stops working,
 * and the security module ends its open sessions once this is saved, as it does a deactivated user's (D-68).
 */
@Service
public class DeactivateAccountUseCase {

    private final PatientAccountRepository accounts;
    private final AccountChangeRepository changes;
    private final ActivationTokenRepository tokens;
    private final CurrentUserPort currentUser;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DeactivateAccountUseCase(PatientAccountRepository accounts, AccountChangeRepository changes,
            ActivationTokenRepository tokens, CurrentUserPort currentUser, ApplicationEventPublisher events,
            Clock clock) {
        this.accounts = accounts;
        this.changes = changes;
        this.tokens = tokens;
        this.currentUser = currentUser;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @throws AccountNotFoundException when there is no such account
     * @throws com.clinic.schedule.portal.domain.exception.AccountStateException when it is inactive already
     */
    @Transactional
    public PatientAccount handle(UUID accountId) {
        PatientAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        AccountUpdate deactivated = account.deactivate(LocalDateTime.now(clock), currentUser.username());
        accounts.save(deactivated.account());
        changes.save(deactivated.change());
        tokens.deleteForAccount(accountId);
        events.publishEvent(new PatientAccountDeactivated(accountId));
        return deactivated.account();
    }
}
