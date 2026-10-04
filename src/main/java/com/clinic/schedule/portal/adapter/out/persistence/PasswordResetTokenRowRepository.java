package com.clinic.schedule.portal.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PasswordResetTokenRowRepository extends JpaRepository<PasswordResetTokenRow, UUID> {

    Optional<PasswordResetTokenRow> findByTokenHash(String tokenHash);
}
