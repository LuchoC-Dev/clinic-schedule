package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.InvalidActivationLinkException;
import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.application.port.ActivationTokenRepository;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.domain.AccountUpdate;
import com.clinic.schedule.portal.domain.ActivationToken;
import com.clinic.schedule.portal.domain.PatientAccount;
import com.clinic.schedule.shared.PasswordPolicy;

/**
 * The patient activates their account from the link in the email, choosing their password (D-79): from 10 to
 * 72 characters, as the staff's (D-68). It is theirs alone from the start, so it is never temporary. The link
 * works once, until it expires: activating it, sending another or deactivating the account ends it, so a link
 * that still exists is one its account waits for.
 *
 * <p>Nobody is logged in: the link is what says whose account it is.
 */
@Service
public class ActivateAccountUseCase {

    private final ActivationTokenRepository tokens;
    private final PatientAccountRepository accounts;
    private final AccountChangeRepository changes;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ActivateAccountUseCase(ActivationTokenRepository tokens, PatientAccountRepository accounts,
            AccountChangeRepository changes, PasswordEncoder passwordEncoder, Clock clock) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.changes = changes;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Whether the link would activate an account, for the screen to ask for the password or say it will not. */
    @Transactional(readOnly = true)
    public boolean isUsable(String token) {
        return pending(token, LocalDateTime.now(clock)).isPresent();
    }

    /**
     * @throws InvalidActivationLinkException when the link activates nothing
     * @throws IllegalArgumentException when the password is too short or too long
     */
    @Transactional
    public void handle(String token, String password) {
        LocalDateTime now = LocalDateTime.now(clock);
        PatientAccount account = pending(token, now).orElseThrow(InvalidActivationLinkException::new);
        AccountUpdate activated = account.activate(passwordEncoder.encode(PasswordPolicy.check(password)), now);
        accounts.save(activated.account());
        changes.save(activated.change());
        tokens.deleteForAccount(account.id());
    }

    private Optional<PatientAccount> pending(String token, LocalDateTime now) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByHash(ActivationToken.hashOf(token))
                .filter(stored -> !stored.isExpiredAt(now))
                .flatMap(stored -> accounts.findById(stored.accountId()));
    }
}
