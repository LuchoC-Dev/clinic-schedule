package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.CopyCode;
import com.clinic.schedule.clinicalrecord.domain.CopyDelivery;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A request for a copy of a record, as a row of {@code record_copy} (D-95). It keeps no content: the copy is the
 * record as it stood at {@code assembled_at}. The version guards what it gains, so two practitioners
 * authenticating it at once are told apart, the second refused.
 */
@Entity
@Table(name = "record_copy")
class RecordCopyRow {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_by_account", nullable = false)
    private String requestedByAccount;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CopyDelivery delivery;

    @Column(nullable = false)
    private String code;

    @Column(name = "assembled_at", nullable = false)
    private LocalDateTime assembledAt;

    @Column(nullable = false)
    private int assembly;

    @Column(name = "authenticated_by")
    private UUID authenticatedBy;

    @Column(name = "authenticated_by_user")
    private String authenticatedByUser;

    @Column(name = "authenticated_at")
    private LocalDateTime authenticatedAt;

    @Column(name = "handed_over_by")
    private String handedOverBy;

    @Column(name = "handed_over_at")
    private LocalDateTime handedOverAt;

    @Version
    private long version;

    protected RecordCopyRow() {
        // required by JPA
    }

    static RecordCopyRow from(RecordCopy copy) {
        RecordCopyRow row = new RecordCopyRow();
        row.id = copy.id().value();
        row.patientId = copy.patientId().value();
        row.requestedBy = copy.requestedBy();
        row.requestedByAccount = copy.requestedByAccount();
        row.requestedAt = copy.requestedAt();
        row.delivery = copy.delivery();
        row.code = copy.code().value();
        row.changesFrom(copy);
        return row;
    }

    /** What a copy gains after it is asked for: put together again, authenticated, handed over. */
    void changesFrom(RecordCopy copy) {
        assembledAt = copy.assembledAt();
        assembly = copy.assembly();
        copy.authentication().ifPresent(authentication -> {
            authenticatedBy = authentication.practitionerId().value();
            authenticatedByUser = authentication.username();
            authenticatedAt = authentication.at();
        });
        copy.handover().ifPresent(handover -> {
            handedOverBy = handover.username();
            handedOverAt = handover.at();
        });
    }

    RecordCopy toCopy() {
        return RecordCopy.restore(new RecordCopyId(id), new PatientId(patientId), requestedBy, requestedByAccount,
                requestedAt, delivery, new CopyCode(code), assembledAt, assembly,
                authenticatedAt == null ? null : new RecordCopy.Authentication(new PractitionerId(authenticatedBy),
                        authenticatedByUser, authenticatedAt),
                handedOverAt == null ? null : new RecordCopy.Handover(handedOverBy, handedOverAt));
    }
}
