package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.portal.application.port.AccountChangeRepository;
import com.clinic.schedule.portal.domain.AccountAction;
import com.clinic.schedule.portal.domain.AccountChange;

/** Implements the port over JPA. */
@Repository
class AccountChangeJpaAdapter implements AccountChangeRepository {

    private final AccountChangeRowRepository rows;

    AccountChangeJpaAdapter(AccountChangeRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(AccountChange change) {
        rows.save(AccountChangeRow.from(change));
    }

    @Override
    public List<AccountChange> findForAccount(UUID accountId) {
        return rows.findByAccountIdOrderByChangedAtDesc(accountId).stream().map(AccountChangeRow::toChange).toList();
    }

    @Override
    public long countSince(UUID accountId, AccountAction action, LocalDateTime since) {
        return rows.countByAccountIdAndActionAndChangedAtGreaterThanEqual(accountId, action, since);
    }
}
