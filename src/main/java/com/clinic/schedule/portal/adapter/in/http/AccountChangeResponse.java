package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDateTime;

import com.clinic.schedule.portal.domain.AccountAction;
import com.clinic.schedule.portal.domain.AccountChange;

/**
 * An entry of a web account's history.
 *
 * @param by the username of whoever at reception did it; null when the patient did it
 */
record AccountChangeResponse(AccountAction action, LocalDateTime at, String by) {

    static AccountChangeResponse of(AccountChange change) {
        return new AccountChangeResponse(change.action(), change.at(), change.by());
    }
}
