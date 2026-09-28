package com.clinic.schedule.user.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.user.api.UserAccount;
import com.clinic.schedule.user.api.UserAccountReader;
import com.clinic.schedule.user.entity.AppUser;
import com.clinic.schedule.user.persistence.AppUserRepository;

/**
 * Answers other modules about users through {@link UserAccountReader}. Read-only: users are
 * created and changed by an admin, through {@link UserAdministrationService} (D-68).
 */
@Service
class UserAccountService implements UserAccountReader {

    private final AppUserRepository users;

    UserAccountService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findActiveByUsername(String username) {
        return users.findByUsernameAndActiveTrue(username).map(UserAccountService::account);
    }

    private static UserAccount account(AppUser user) {
        return new UserAccount(user.getUsername(), user.getPasswordHash(), user.getRole(), user.getPractitionerId());
    }
}
