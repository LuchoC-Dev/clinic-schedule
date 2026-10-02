package com.clinic.schedule.portal.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AccountChangeRowRepository extends JpaRepository<AccountChangeRow, UUID> {

    List<AccountChangeRow> findByAccountIdOrderByChangedAtDesc(UUID accountId);
}
