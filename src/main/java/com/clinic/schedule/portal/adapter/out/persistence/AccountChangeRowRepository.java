package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clinic.schedule.portal.domain.AccountAction;

interface AccountChangeRowRepository extends JpaRepository<AccountChangeRow, UUID> {

    List<AccountChangeRow> findByAccountIdOrderByChangedAtDesc(UUID accountId);

    long countByAccountIdAndActionAndChangedAtGreaterThanEqual(UUID accountId, AccountAction action,
            LocalDateTime since);
}
