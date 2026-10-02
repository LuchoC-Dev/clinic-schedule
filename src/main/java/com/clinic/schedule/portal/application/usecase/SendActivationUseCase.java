package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.AccountNotFoundException;
import com.clinic.schedule.portal.application.exception.UnknownPatientException;
import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.application.port.AccountHolderPort;
import com.clinic.schedule.portal.application.port.AccountMailPort;
import com.clinic.schedule.portal.application.port.ActivationTokenRepository;
import com.clinic.schedule.portal.application.port.CurrentUserPort;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.domain.AccountChange;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.domain.ActivationMessage;
import com.clinic.schedule.portal.domain.ActivationToken;
import com.clinic.schedule.portal.domain.PatientAccount;

/**
 * Sends a pending account the link to activate it (D-79), replacing any link sent before: only the last one
 * works. Reception asks for it again from the account's screen; opening an account, and changing the email
 * of one still pending, send it too, joining their transaction.
 *
 * <p>One attempt. A sending that fails leaves the account as it was and says so, for reception to send it
 * again; only one that went out is recorded.
 */
@Service
public class SendActivationUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendActivationUseCase.class);

    private final PatientAccountRepository accounts;
    private final AccountChangeRepository changes;
    private final ActivationTokenRepository tokens;
    private final AccountHolderPort holders;
    private final AccountMailPort mail;
    private final CurrentUserPort currentUser;
    private final Clock clock;
    private final Duration validFor;

    public SendActivationUseCase(PatientAccountRepository accounts, AccountChangeRepository changes,
            ActivationTokenRepository tokens, AccountHolderPort holders, AccountMailPort mail,
            CurrentUserPort currentUser, Clock clock, @Value("${app.portal.activation-valid-for}") String validFor) {
        this.accounts = accounts;
        this.changes = changes;
        this.tokens = tokens;
        this.holders = holders;
        this.mail = mail;
        this.currentUser = currentUser;
        this.clock = clock;
        this.validFor = Duration.parse(validFor);
        if (this.validFor.isNegative() || this.validFor.isZero()) {
            throw new IllegalArgumentException("An activation link lasts for some time");
        }
    }

    /**
     * @return whether the email went out
     * @throws AccountNotFoundException when there is no such account
     * @throws com.clinic.schedule.portal.domain.exception.AccountStateException unless it waits to be activated
     */
    @Transactional
    public boolean handle(UUID accountId) {
        PatientAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        LocalDateTime now = LocalDateTime.now(clock);
        AccountChange sent = account.activationSent(now, currentUser.username());
        AccountHolder holder = holders.find(account.patientId())
                .orElseThrow(() -> new UnknownPatientException(account.patientId()));
        ActivationToken.Issued issued = ActivationToken.issue(account.id(), now, validFor);
        tokens.save(issued.stored());
        try {
            mail.sendActivation(new ActivationMessage(account.email(), holder.fullName(), issued.token(),
                    issued.stored().expiresAt()));
        } catch (RuntimeException e) {
            log.warn("The activation of account {} did not go out: {}", account.id(), e.getMessage());
            return false;
        }
        changes.save(sent);
        return true;
    }
}
