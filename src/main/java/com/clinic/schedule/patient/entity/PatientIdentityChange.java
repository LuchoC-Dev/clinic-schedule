package com.clinic.schedule.patient.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A change to who a patient is (D-60): the name, the document and how far they are known, before
 * and after, with who changed them and when. The patient stays the same one — everything else
 * points at them by an id that never changes — so the history is what tells how they came to be
 * who they are now: an alias replaced by a name, a document added, a document seen.
 *
 * <p>Only {@link Patient#changeIdentity} makes one, and nothing changes one once made.
 */
@Entity
@Table(name = "patient_identity_change")
public class PatientIdentityChange {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Column(name = "full_name_before", nullable = false, length = 150)
    private String fullNameBefore;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "type", column = @Column(name = "document_type_before", length = 20)),
            @AttributeOverride(name = "number", column = @Column(name = "document_number_before", length = 20))})
    private IdentityDocument documentBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_status_before", nullable = false, length = 10)
    private IdentityStatus statusBefore;

    @Column(name = "full_name_after", nullable = false, length = 150)
    private String fullNameAfter;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "type", column = @Column(name = "document_type_after", length = 20)),
            @AttributeOverride(name = "number", column = @Column(name = "document_number_after", length = 20))})
    private IdentityDocument documentAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_status_after", nullable = false, length = 10)
    private IdentityStatus statusAfter;

    protected PatientIdentityChange() {
        // required by JPA
    }

    PatientIdentityChange(UUID patientId, LocalDateTime changedAt, String changedBy,
            String fullNameBefore, IdentityDocument documentBefore, IdentityStatus statusBefore,
            String fullNameAfter, IdentityDocument documentAfter, IdentityStatus statusAfter) {
        if (changedAt == null || changedBy == null || changedBy.isBlank()) {
            throw new IllegalArgumentException("A change of identity needs when and by whom");
        }
        this.id = UUID.randomUUID();
        this.patientId = patientId;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
        this.fullNameBefore = fullNameBefore;
        this.documentBefore = documentBefore;
        this.statusBefore = statusBefore;
        this.fullNameAfter = fullNameAfter;
        this.documentAfter = documentAfter;
        this.statusAfter = statusAfter;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    /** The username of whoever changed it. */
    public String getChangedBy() {
        return changedBy;
    }

    public String getFullNameBefore() {
        return fullNameBefore;
    }

    /** Null when there was none. */
    public IdentityDocument getDocumentBefore() {
        return documentBefore;
    }

    public IdentityStatus getStatusBefore() {
        return statusBefore;
    }

    public String getFullNameAfter() {
        return fullNameAfter;
    }

    /** Null when there is none. */
    public IdentityDocument getDocumentAfter() {
        return documentAfter;
    }

    public IdentityStatus getStatusAfter() {
        return statusAfter;
    }
}
