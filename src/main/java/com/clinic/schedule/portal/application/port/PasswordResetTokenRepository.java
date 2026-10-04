package com.clinic.schedule.portal.application.port;

import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.portal.domain.PasswordResetToken;

/** Where the links to choose a new password are kept (D-86): one per account at most, a new one replacing it. */
public interface PasswordResetTokenRepository {

    void save(PasswordResetToken token);

    Optional<PasswordResetToken> findByHash(String hash);

    Optional<PasswordResetToken> findForAccount(UUID accountId);

    void deleteForAccount(UUID accountId);
}
