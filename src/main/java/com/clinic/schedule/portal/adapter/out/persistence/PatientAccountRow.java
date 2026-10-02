package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.domain.AccountStatus;
import com.clinic.schedule.portal.domain.PatientAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A web account as a row (D-79). */
@Entity
@Table(name = "patient_account")
class PatientAccountRow {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false, unique = true)
    private UUID patientId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus status;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    protected PatientAccountRow() {
        // required by JPA
    }

    static PatientAccountRow from(PatientAccount account) {
        PatientAccountRow row = new PatientAccountRow();
        row.id = account.id();
        row.patientId = account.patientId();
        row.email = account.email();
        row.passwordHash = account.passwordHash();
        row.status = account.status();
        row.openedAt = account.openedAt();
        return row;
    }

    PatientAccount toAccount() {
        return PatientAccount.restore(id, patientId, email, passwordHash, status, openedAt);
    }
}
