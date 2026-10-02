package com.clinic.schedule.portal.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.portal.application.port.ActivationTokenRepository;
import com.clinic.schedule.portal.domain.ActivationToken;

/** Implements the port over JPA: the row is the account's, so saving one replaces the last. */
@Repository
class ActivationTokenJpaAdapter implements ActivationTokenRepository {

    private final ActivationTokenRowRepository rows;

    ActivationTokenJpaAdapter(ActivationTokenRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(ActivationToken token) {
        rows.save(ActivationTokenRow.from(token));
    }

    @Override
    public Optional<ActivationToken> findByHash(String hash) {
        return rows.findByTokenHash(hash).map(ActivationTokenRow::toToken);
    }

    @Override
    public void deleteForAccount(UUID accountId) {
        rows.deleteById(accountId);
    }
}
