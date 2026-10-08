package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.portal.application.port.WebRegistrationRepository;
import com.clinic.schedule.portal.domain.WebRegistration;

/** Implements the port over JPA. */
@Repository
class WebRegistrationJpaAdapter implements WebRegistrationRepository {

    private final WebRegistrationRowRepository rows;

    WebRegistrationJpaAdapter(WebRegistrationRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(WebRegistration registration) {
        rows.save(WebRegistrationRow.from(registration));
    }

    @Override
    public Optional<WebRegistration> findByHash(String hash) {
        return rows.findByTokenHash(hash).map(WebRegistrationRow::toRegistration);
    }

    @Override
    public void delete(UUID id) {
        rows.deleteById(id);
    }

    @Override
    public int deleteExpiredAt(LocalDateTime now) {
        return (int) rows.deleteByExpiresAtLessThanEqual(now);
    }
}
