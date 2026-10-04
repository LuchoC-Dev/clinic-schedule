package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.domain.FailedLogin;

/**
 * Keeps count of a web account's logins (D-86): {@code app.portal.login-attempts} failed in a row lock it for
 * {@code app.portal.lock-for}, recorded; one that goes through starts the count again. The security module
 * tells it of each, as it logs patients in.
 */
@Service
public class RecordLoginUseCase {

    private final PatientAccountRepository accounts;
    private final AccountChangeRepository changes;
    private final Clock clock;
    private final int attempts;
    private final Duration lockFor;

    public RecordLoginUseCase(PatientAccountRepository accounts, AccountChangeRepository changes, Clock clock,
            @Value("${app.portal.login-attempts}") int attempts, @Value("${app.portal.lock-for}") Duration lockFor) {
        this.accounts = accounts;
        this.changes = changes;
        this.clock = clock;
        this.attempts = attempts;
        this.lockFor = lockFor;
    }

    @Transactional
    public void failed(UUID accountId) {
        accounts.findById(accountId).ifPresent(account -> {
            FailedLogin failed = account.loginFailed(LocalDateTime.now(clock), attempts, lockFor);
            accounts.save(failed.account());
            if (failed.locked() != null) {
                changes.save(failed.locked());
            }
        });
    }

    @Transactional
    public void succeeded(UUID accountId) {
        accounts.findById(accountId).flatMap(account -> account.loginSucceeded()).ifPresent(accounts::save);
    }
}
