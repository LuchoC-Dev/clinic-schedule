package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.AccessGrant;
import com.clinic.schedule.clinicalrecord.domain.AccessGrantId;
import com.clinic.schedule.clinicalrecord.domain.Approval;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An access grant as a row of {@code record_access_grant}. Separate from the aggregate (D-16). */
@Entity
@Table(name = "record_access_grant")
class AccessGrantRow {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    /** One of the check and the copy, the other null (D-95). */
    @Column(name = "check_id")
    private UUID checkId;

    @Column(name = "record_copy_id")
    private UUID recordCopyId;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Approval approval;

    protected AccessGrantRow() {
        // required by JPA
    }

    static AccessGrantRow from(AccessGrant grant) {
        AccessGrantRow row = new AccessGrantRow();
        row.id = grant.id().value();
        row.patientId = grant.patientId().value();
        row.practitionerId = grant.practitionerId().value();
        row.checkId = grant.checkId().map(CheckId::value).orElse(null);
        row.recordCopyId = grant.recordCopyId().map(RecordCopyId::value).orElse(null);
        row.grantedAt = grant.grantedAt();
        row.validUntil = grant.validUntil();
        row.reason = grant.reason().orElse(null);
        row.approval = grant.approval();
        return row;
    }

    AccessGrant toGrant() {
        return AccessGrant.restore(new AccessGrantId(id), new PatientId(patientId), new PractitionerId(practitionerId),
                checkId == null ? null : new CheckId(checkId), recordCopyId == null ? null : new RecordCopyId(recordCopyId),
                grantedAt, validUntil, reason, approval);
    }
}
