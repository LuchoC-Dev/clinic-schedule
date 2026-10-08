package com.clinic.schedule.portal.application.port;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.portal.domain.WebRegistration;

/** Where the registrations made on the web wait for their confirmation (D-91). */
public interface WebRegistrationRepository {

    void save(WebRegistration registration);

    Optional<WebRegistration> findByHash(String hash);

    void delete(UUID id);

    /** @return how many registrations, expired by then and never confirmed, were forgotten */
    int deleteExpiredAt(LocalDateTime now);
}
