package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.UnknownPatientException;
import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.application.port.AccountHolderPort;
import com.clinic.schedule.portal.application.port.CurrentUserPort;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.domain.AccountOpeningService;
import com.clinic.schedule.portal.domain.AccountUpdate;
import com.clinic.schedule.portal.domain.PatientAccount;

/**
 * Reception opens a web account for a patient, at the desk, and the patient gets the link to activate it
 * at the account's email (D-79). Whether the patient may have one is {@link AccountOpeningService}'s to say;
 * this fetches what it needs. The account is opened even when the email does not go out, and the answer says
 * so, for reception to send it again.
 *
 * <p>A second account for the same patient, opened at once by two receptionists, is refused by the
 * database's unique patient (D-09).
 */
@Service
public class OpenAccountUseCase {

    private final AccountHolderPort holders;
    private final PatientAccountRepository accounts;
    private final AccountChangeRepository changes;
    private final SendActivationUseCase activation;
    private final CurrentUserPort currentUser;
    private final Clock clock;

    public OpenAccountUseCase(AccountHolderPort holders, PatientAccountRepository accounts,
            AccountChangeRepository changes, SendActivationUseCase activation, CurrentUserPort currentUser,
            Clock clock) {
        this.holders = holders;
        this.accounts = accounts;
        this.changes = changes;
        this.activation = activation;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * @throws UnknownPatientException when there is no such patient
     * @throws com.clinic.schedule.portal.domain.exception.AccountRefusedException when they may not have one
     * @throws IllegalArgumentException when the email cannot be one
     */
    @Transactional
    public Result handle(Command command) {
        AccountHolder holder = holders.find(command.patientId())
                .orElseThrow(() -> new UnknownPatientException(command.patientId()));
        AccountUpdate opened = AccountOpeningService.open(holder,
                accounts.findByPatientId(holder.patientId()).isPresent(), command.email(),
                LocalDateTime.now(clock), currentUser.username());
        accounts.save(opened.account());
        changes.save(opened.change());
        return new Result(opened.account(), activation.handle(opened.account().id()));
    }

    public record Command(UUID patientId, String email) {
    }

    /** The account opened, and whether its activation went out. */
    public record Result(PatientAccount account, boolean activationSent) {
    }
}
