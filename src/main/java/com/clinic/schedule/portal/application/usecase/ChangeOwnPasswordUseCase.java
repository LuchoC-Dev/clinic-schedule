package com.clinic.schedule.portal.application.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.portal.application.exception.OwnPasswordRefusedException;
import com.clinic.schedule.portal.application.exception.OwnPasswordRefusedException.Reason;
import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.application.port.CurrentPatientPort;
import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.domain.AccountUpdate;
import com.clinic.schedule.portal.domain.PatientAccount;
import com.clinic.schedule.shared.PasswordPolicy;

/**
 * The patient logged in changes their password (D-86), with the staff's rules (D-69): giving the current one,
 * a new one from 10 to 72 characters, not the same. Their session goes on.
 */
@Service
public class ChangeOwnPasswordUseCase {

    private final CurrentPatientPort currentPatient;
    private final PatientAccountRepository accounts;
    private final AccountChangeRepository changes;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ChangeOwnPasswordUseCase(CurrentPatientPort currentPatient, PatientAccountRepository accounts,
            AccountChangeRepository changes, PasswordEncoder passwordEncoder, Clock clock) {
        this.currentPatient = currentPatient;
        this.accounts = accounts;
        this.changes = changes;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * @throws OwnPasswordRefusedException when the current one is wrong, or the new one is the same
     * @throws IllegalArgumentException when the new one is too short or too long
     */
    @Transactional
    public void handle(String currentPassword, String newPassword) {
        PatientAccount account = accounts.findByPatientId(currentPatient.patientId())
                .orElseThrow(() -> new IllegalStateException("No web account for the patient logged in"));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, account.passwordHash())) {
            throw new OwnPasswordRefusedException(Reason.WRONG_CURRENT, "The current password is not right");
        }
        PasswordPolicy.check(newPassword);
        if (newPassword.equals(currentPassword)) {
            throw new OwnPasswordRefusedException(Reason.SAME, "The new password is the current one");
        }
        AccountUpdate changed = account.changePassword(passwordEncoder.encode(newPassword), LocalDateTime.now(clock));
        accounts.save(changed.account());
        changes.save(changed.change());
    }
}
