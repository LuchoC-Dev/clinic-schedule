package com.clinic.schedule.user.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.security.api.CurrentUserReader;
import com.clinic.schedule.user.entity.AppUser;
import com.clinic.schedule.user.entity.PasswordPolicy;
import com.clinic.schedule.user.persistence.AppUserChangeRepository;
import com.clinic.schedule.user.persistence.AppUserRepository;

/**
 * Whoever is logged in changes their own password (D-69): giving the current one, for a new one that
 * follows the policy and is not the same. It is how a temporary password stops being one, and the
 * change is recorded, by the user themselves.
 */
@Service
public class PasswordChangeService {

    private final AppUserRepository users;
    private final AppUserChangeRepository changes;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserReader currentUser;
    private final Clock clock;

    PasswordChangeService(AppUserRepository users, AppUserChangeRepository changes, PasswordEncoder passwordEncoder,
            CurrentUserReader currentUser, Clock clock) {
        this.users = users;
        this.changes = changes;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * @throws WrongPasswordException when the current password is not theirs
     * @throws UnchangedPasswordException when the new one is the current one
     * @throws IllegalArgumentException when the new one does not follow the policy
     */
    @Transactional
    public void change(String currentPassword, String newPassword) {
        AppUser user = users.findByUsernameAndActiveTrue(currentUser.username())
                .orElseThrow(() -> new IllegalStateException("Only an active user changes their password"));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new WrongPasswordException();
        }
        PasswordPolicy.check(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new UnchangedPasswordException();
        }
        changes.save(user.changePassword(passwordEncoder.encode(newPassword), LocalDateTime.now(clock)));
        users.save(user);
    }
}
