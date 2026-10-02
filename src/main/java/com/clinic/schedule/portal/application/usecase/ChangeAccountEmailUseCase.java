package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.AccountNotFoundException;
import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.application.port.CurrentUserPort;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.domain.AccountStatus;
import com.clinic.schedule.portal.domain.AccountUpdate;
import com.clinic.schedule.portal.domain.PatientAccount;

/**
 * Reception changes a web account's email, with the patient's document in sight: the only way it changes,
 * so that changing the patient's contact can never hand the account to someone else (D-79). It holds at
 * once. An account still pending gets its activation at the new address, and the link sent to the old one
 * stops working.
 */
@Service
public class ChangeAccountEmailUseCase {

    private final PatientAccountRepository accounts;
    private final AccountChangeRepository changes;
    private final SendActivationUseCase activation;
    private final CurrentUserPort currentUser;
    private final Clock clock;

    public ChangeAccountEmailUseCase(PatientAccountRepository accounts, AccountChangeRepository changes,
            SendActivationUseCase activation, CurrentUserPort currentUser, Clock clock) {
        this.accounts = accounts;
        this.changes = changes;
        this.activation = activation;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * @throws AccountNotFoundException when there is no such account
     * @throws IllegalArgumentException when the email cannot be one
     */
    @Transactional
    public Result handle(UUID accountId, String email) {
        PatientAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        Optional<AccountUpdate> changed = account.changeEmail(email, LocalDateTime.now(clock), currentUser.username());
        if (changed.isEmpty()) {
            return new Result(account, false);
        }
        accounts.save(changed.get().account());
        changes.save(changed.get().change());
        boolean sent = changed.get().account().status() == AccountStatus.PENDING && activation.handle(accountId);
        return new Result(changed.get().account(), sent);
    }

    /** The account as it became, and whether an activation went out to the new address. */
    public record Result(PatientAccount account, boolean activationSent) {
    }
}
