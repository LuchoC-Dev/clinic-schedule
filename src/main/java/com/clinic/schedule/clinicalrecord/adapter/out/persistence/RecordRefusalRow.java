package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.RecordRefusal;
import com.clinic.schedule.clinicalrecord.domain.RecordRefusalId;
import com.clinic.schedule.clinicalrecord.domain.RefusedAttempt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A refused attempt as a row of {@code record_refusal}. Separate from the domain object (D-16). */
@Entity
@Table(name = "record_refusal")
class RecordRefusalRow {

    @Id
    private UUID id;

    @Column(name = "refused_by", nullable = false, length = 50)
    private String refusedBy;

    @Column(name = "practitioner_id")
    private UUID practitionerId;

    @Column(name = "refused_at", nullable = false)
    private LocalDateTime refusedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RefusedAttempt attempt;

    @Column(name = "patient_id")
    private UUID patientId;

    /** No foreign key: the file asked for may not exist. */
    @Column(name = "attachment_id")
    private UUID attachmentId;

    @Column(length = 300)
    private String route;

    protected RecordRefusalRow() {
        // required by JPA
    }

    static RecordRefusalRow from(RecordRefusal refusal) {
        RecordRefusalRow row = new RecordRefusalRow();
        row.id = refusal.id().value();
        row.refusedBy = refusal.refusedBy();
        row.practitionerId = refusal.practitionerId().map(PractitionerId::value).orElse(null);
        row.refusedAt = refusal.refusedAt();
        row.attempt = refusal.attempt();
        row.patientId = refusal.patientId().map(PatientId::value).orElse(null);
        row.attachmentId = refusal.attachmentId().map(AttachmentId::value).orElse(null);
        row.route = refusal.route().orElse(null);
        return row;
    }

    RecordRefusal toDomain() {
        return RecordRefusal.restore(new RecordRefusalId(id), refusedBy,
                practitionerId == null ? null : new PractitionerId(practitionerId), refusedAt, attempt,
                patientId == null ? null : new PatientId(patientId),
                attachmentId == null ? null : new AttachmentId(attachmentId), route);
    }
}
