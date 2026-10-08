package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface WebRegistrationRowRepository extends JpaRepository<WebRegistrationRow, UUID> {

    Optional<WebRegistrationRow> findByTokenHash(String tokenHash);

    long deleteByExpiresAtLessThanEqual(LocalDateTime now);
}
