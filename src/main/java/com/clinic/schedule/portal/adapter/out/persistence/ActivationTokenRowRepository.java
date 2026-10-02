package com.clinic.schedule.portal.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ActivationTokenRowRepository extends JpaRepository<ActivationTokenRow, UUID> {

    Optional<ActivationTokenRow> findByTokenHash(String tokenHash);
}
