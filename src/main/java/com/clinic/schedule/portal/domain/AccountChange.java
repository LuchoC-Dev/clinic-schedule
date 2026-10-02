package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One entry of a web account's history (D-79): what was done, when, and by whom — reception's username, or
 * nobody for what the patient did themselves, which so far is activating it. Only {@link PatientAccount}
 * makes one, and nothing changes one once made.
 *
 * @param by the username of whoever at reception did it; null when the patient did it
 */
public record AccountChange(UUID id, UUID accountId, AccountAction action, LocalDateTime at, String by) {

    public AccountChange {
        if (id == null || accountId == null || action == null || at == null) {
            throw new IllegalArgumentException("A change to an account needs its account, what and when");
        }
        if ((action == AccountAction.ACTIVATED) != (by == null)) {
            throw new IllegalArgumentException("The patient activates their account; reception does the rest");
        }
    }

    static AccountChange of(UUID accountId, AccountAction action, LocalDateTime at, String by) {
        return new AccountChange(UUID.randomUUID(), accountId, action, at, by);
    }
}
