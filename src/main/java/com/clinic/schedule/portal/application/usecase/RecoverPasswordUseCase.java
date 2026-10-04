package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.api.PatientPasswordReset;
import com.clinic.schedule.portal.application.exception.InvalidResetLinkException;
import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.application.port.AccountHolderPort;
import com.clinic.schedule.portal.application.port.AccountMailPort;
import com.clinic.schedule.portal.application.port.PasswordResetTokenRepository;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.domain.AccountStatus;
import com.clinic.schedule.portal.domain.AccountUpdate;
import com.clinic.schedule.portal.domain.PasswordResetMessage;
import com.clinic.schedule.portal.domain.PasswordResetToken;
import com.clinic.schedule.portal.domain.PatientAccount;
import com.clinic.schedule.shared.PasswordPolicy;

/**
 * A patient who forgot their password chooses a new one from a link sent to their account's email (D-86).
 *
 * <p>Asking for it, with the document, answers nothing either way, so the form tells nobody whether a document
 * has an account: an account in use gets the link, valid for {@code app.portal.recovery-valid-for}, unless it
 * got one less than {@code app.portal.recovery-every} ago, so the form cannot flood a mailbox. Choosing the new
 * password lifts a lock, ends the link and the account's open sessions.
 *
 * <p>Nobody is logged in: the document, and then the link, say whose account it is.
 */
@Service
public class RecoverPasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecoverPasswordUseCase.class);

    private final AccountHolderPort holders;
    private final PatientAccountRepository accounts;
    private final PasswordResetTokenRepository tokens;
    private final AccountChangeRepository changes;
    private final AccountMailPort mail;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration validFor;
    private final Duration every;

    public RecoverPasswordUseCase(AccountHolderPort holders, PatientAccountRepository accounts,
            PasswordResetTokenRepository tokens, AccountChangeRepository changes, AccountMailPort mail,
            PasswordEncoder passwordEncoder, ApplicationEventPublisher events, Clock clock,
            @Value("${app.portal.recovery-valid-for}") Duration validFor,
            @Value("${app.portal.recovery-every}") Duration every) {
        this.holders = holders;
        this.accounts = accounts;
        this.tokens = tokens;
        this.changes = changes;
        this.mail = mail;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
        this.validFor = validFor;
        this.every = every;
    }

    /** Sends the link, when there is an account in use for the document and none went out too recently. */
    @Transactional
    public void ask(String documentType, String documentNumber) {
        if (documentType == null || documentNumber == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        holders.findByDocument(documentType.strip(), documentNumber.strip())
                .flatMap(accounts::findByPatientId)
                .filter(account -> account.status() == AccountStatus.ACTIVE)
                .filter(account -> tokens.findForAccount(account.id())
                        .map(sent -> !sent.isRecentAt(now, every))
                        .orElse(true))
                .ifPresent(account -> send(account, now));
    }

    private void send(PatientAccount account, LocalDateTime now) {
        AccountHolder holder = holders.find(account.patientId())
                .orElseThrow(() -> new IllegalStateException("No patient for the account"));
        PasswordResetToken.Issued issued = PasswordResetToken.issue(account.id(), now, validFor);
        try {
            mail.sendPasswordReset(new PasswordResetMessage(account.email(), holder.fullName(), issued.token(),
                    issued.stored().expiresAt()));
        } catch (RuntimeException e) {
            log.warn("The link to reset a password did not go out: {}", e.getMessage());
            return;
        }
        tokens.save(issued.stored());
        changes.save(account.recoverySent(now));
    }

    /** Whether the link would let a password be chosen, for the screen to ask for it or say it will not. */
    @Transactional(readOnly = true)
    public boolean isUsable(String token) {
        return accountOf(token, LocalDateTime.now(clock)).isPresent();
    }

    /**
     * @throws InvalidResetLinkException when the link resets nothing
     * @throws IllegalArgumentException when the password is too short or too long
     */
    @Transactional
    public void reset(String token, String password) {
        LocalDateTime now = LocalDateTime.now(clock);
        PatientAccount account = accountOf(token, now).orElseThrow(InvalidResetLinkException::new);
        AccountUpdate reset = account.resetPassword(passwordEncoder.encode(PasswordPolicy.check(password)), now);
        accounts.save(reset.account());
        changes.save(reset.change());
        tokens.deleteForAccount(account.id());
        events.publishEvent(new PatientPasswordReset(account.id()));
    }

    private Optional<PatientAccount> accountOf(String token, LocalDateTime now) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByHash(PasswordResetToken.hashOf(token))
                .filter(stored -> !stored.isExpiredAt(now))
                .flatMap(stored -> accounts.findById(stored.accountId()))
                .filter(account -> account.status() == AccountStatus.ACTIVE);
    }
}
