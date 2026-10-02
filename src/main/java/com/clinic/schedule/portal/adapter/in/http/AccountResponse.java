package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.domain.AccountStatus;
import com.clinic.schedule.portal.domain.PatientAccount;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A web account as reception reads it (D-79). Never the hash of its password.
 *
 * @param activationSent whether an activation went out with what was just done; left out when nothing was to
 *        go out
 */
record AccountResponse(UUID id, UUID patientId, String email, AccountStatus status, LocalDateTime openedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean activationSent) {

    static AccountResponse of(PatientAccount account) {
        return new AccountResponse(account.id(), account.patientId(), account.email(), account.status(),
                account.openedAt(), null);
    }

    static AccountResponse of(PatientAccount account, boolean activationSent) {
        return new AccountResponse(account.id(), account.patientId(), account.email(), account.status(),
                account.openedAt(), activationSent);
    }
}
