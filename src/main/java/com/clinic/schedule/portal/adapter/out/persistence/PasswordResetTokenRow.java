package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.domain.PasswordResetToken;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An account's link to choose a new password as a row (D-86): keyed by the account, since it has one at most,
 * and keeping only the hash of the token.
 */
@Entity
@Table(name = "patient_account_password_reset")
class PasswordResetTokenRow {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected PasswordResetTokenRow() {
        // required by JPA
    }

    static PasswordResetTokenRow from(PasswordResetToken token) {
        PasswordResetTokenRow row = new PasswordResetTokenRow();
        row.accountId = token.accountId();
        row.tokenHash = token.hash();
        row.issuedAt = token.issuedAt();
        row.expiresAt = token.expiresAt();
        return row;
    }

    PasswordResetToken toToken() {
        return new PasswordResetToken(accountId, tokenHash, issuedAt, expiresAt);
    }
}
