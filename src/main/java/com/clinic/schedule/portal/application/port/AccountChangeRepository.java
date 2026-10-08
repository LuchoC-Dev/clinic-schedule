package com.clinic.schedule.portal.application.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.portal.domain.AccountAction;
import com.clinic.schedule.portal.domain.AccountChange;

/** Where the history of each web account is kept. Only ever added to. */
public interface AccountChangeRepository {

    void save(AccountChange change);

    /** The account's history, the most recent first. */
    List<AccountChange> findForAccount(UUID accountId);

    /** How many times that was done to the account from then on. */
    long countSince(UUID accountId, AccountAction action, LocalDateTime since);
}
