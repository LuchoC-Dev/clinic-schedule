package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.domain.AccountAction;
import com.clinic.schedule.portal.domain.AccountChange;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An entry of a web account's history as a row (D-79). Only ever inserted. */
@Entity
@Table(name = "patient_account_change")
class AccountChangeRow {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountAction action;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /** Null when the patient did it. */
    @Column(name = "changed_by", length = 50)
    private String changedBy;

    protected AccountChangeRow() {
        // required by JPA
    }

    static AccountChangeRow from(AccountChange change) {
        AccountChangeRow row = new AccountChangeRow();
        row.id = change.id();
        row.accountId = change.accountId();
        row.action = change.action();
        row.changedAt = change.at();
        row.changedBy = change.by();
        return row;
    }

    AccountChange toChange() {
        return new AccountChange(id, accountId, action, changedAt, changedBy);
    }
}
