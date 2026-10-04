package com.clinic.schedule.portal.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.portal.application.port.PasswordResetTokenRepository;
import com.clinic.schedule.portal.domain.PasswordResetToken;

/** Implements the port over JPA. */
@Repository
class PasswordResetTokenJpaAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenRowRepository rows;

    PasswordResetTokenJpaAdapter(PasswordResetTokenRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(PasswordResetToken token) {
        rows.save(PasswordResetTokenRow.from(token));
    }

    @Override
    public Optional<PasswordResetToken> findByHash(String hash) {
        return rows.findByTokenHash(hash).map(PasswordResetTokenRow::toToken);
    }

    @Override
    public Optional<PasswordResetToken> findForAccount(UUID accountId) {
        return rows.findById(accountId).map(PasswordResetTokenRow::toToken);
    }

    @Override
    public void deleteForAccount(UUID accountId) {
        rows.deleteById(accountId);
    }
}
