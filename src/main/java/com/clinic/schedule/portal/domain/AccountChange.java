package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * One entry of a web account's history (D-79): what was done, when, and by whom — reception's username, or
 * nobody for what the patient or the web did: activating it, recovering or changing the password, a link
 * asked for, a lock (D-86), registering on the web (D-91), a child asked for (D-92). Only {@link PatientAccount} makes one, and nothing
 * changes one once made.
 *
 * @param by the username of whoever at reception did it; null when the patient or the web did it
 */
public record AccountChange(UUID id, UUID accountId, AccountAction action, LocalDateTime at, String by) {

    /** What nobody at reception does: the account's own doing has no username of the staff's. */
    private static final Set<AccountAction> BY_NOBODY = EnumSet.of(AccountAction.ACTIVATED,
            AccountAction.RECOVERY_SENT, AccountAction.PASSWORD_RESET, AccountAction.PASSWORD_CHANGED,
            AccountAction.LOCKED, AccountAction.REGISTERED, AccountAction.CHILD_REQUESTED);

    public AccountChange {
        if (id == null || accountId == null || action == null || at == null) {
            throw new IllegalArgumentException("A change to an account needs its account, what and when");
        }
        if (BY_NOBODY.contains(action) != (by == null)) {
            throw new IllegalArgumentException("Reception does what the patient and the web do not");
        }
    }

    static AccountChange of(UUID accountId, AccountAction action, LocalDateTime at, String by) {
        return new AccountChange(UUID.randomUUID(), accountId, action, at, by);
    }
}
