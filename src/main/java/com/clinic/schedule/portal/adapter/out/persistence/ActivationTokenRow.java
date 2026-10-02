package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.domain.ActivationToken;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An account's pending activation as a row (D-79): keyed by the account, since it has one at most, and
 * keeping only the hash of the token.
 */
@Entity
@Table(name = "patient_account_activation")
class ActivationTokenRow {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected ActivationTokenRow() {
        // required by JPA
    }

    static ActivationTokenRow from(ActivationToken token) {
        ActivationTokenRow row = new ActivationTokenRow();
        row.accountId = token.accountId();
        row.tokenHash = token.hash();
        row.expiresAt = token.expiresAt();
        return row;
    }

    ActivationToken toToken() {
        return new ActivationToken(accountId, tokenHash, expiresAt);
    }
}
