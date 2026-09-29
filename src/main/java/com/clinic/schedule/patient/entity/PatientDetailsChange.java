package com.clinic.schedule.patient.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A change to one of a patient's details (D-73): which one, what it was and what it became, with who
 * changed it and when. One entry per detail, so a save that changes the phone and the email leaves
 * two, and a detail added later needs no new columns.
 *
 * <p>The value is the detail as it is kept — a date as {@code 1981-03-12}, a phone's digits, an email
 * — and the person it belonged to apart, so a screen can say it in its own language. Null means
 * there was none.
 *
 * <p>Only {@link Patient#changeDetails} makes one, and nothing changes one once made.
 */
@Entity
@Table(name = "patient_details_change")
public class PatientDetailsChange {

    /** Which detail changed. */
    public enum Field {
        BIRTH_DATE, PHONE, EMAIL
    }

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Field field;

    @Column(name = "value_before", length = 254)
    private String valueBefore;

    @Column(name = "owner_name_before", length = 150)
    private String ownerNameBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_relation_before", length = 20)
    private Relation ownerRelationBefore;

    @Column(name = "value_after", length = 254)
    private String valueAfter;

    @Column(name = "owner_name_after", length = 150)
    private String ownerNameAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_relation_after", length = 20)
    private Relation ownerRelationAfter;

    protected PatientDetailsChange() {
        // required by JPA
    }

    PatientDetailsChange(UUID patientId, LocalDateTime changedAt, String changedBy, Field field,
            String valueBefore, Responsible ownerBefore, String valueAfter, Responsible ownerAfter) {
        if (changedAt == null || changedBy == null || changedBy.isBlank()) {
            throw new IllegalArgumentException("A change of a patient's details needs when and by whom");
        }
        this.id = UUID.randomUUID();
        this.patientId = patientId;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
        this.field = field;
        this.valueBefore = valueBefore;
        this.ownerNameBefore = ownerBefore == null ? null : ownerBefore.name();
        this.ownerRelationBefore = ownerBefore == null ? null : ownerBefore.relation();
        this.valueAfter = valueAfter;
        this.ownerNameAfter = ownerAfter == null ? null : ownerAfter.name();
        this.ownerRelationAfter = ownerAfter == null ? null : ownerAfter.relation();
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

    public Field getField() {
        return field;
    }

    /** Null when there was none. */
    public String getValueBefore() {
        return valueBefore;
    }

    /** Null when it was the patient's own, or there was none. */
    public Responsible getOwnerBefore() {
        return ownerRelationBefore == null ? null : new Responsible(ownerNameBefore, ownerRelationBefore);
    }

    /** Null when there is none. */
    public String getValueAfter() {
        return valueAfter;
    }

    /** Null when it is the patient's own, or there is none. */
    public Responsible getOwnerAfter() {
        return ownerRelationAfter == null ? null : new Responsible(ownerNameAfter, ownerRelationAfter);
    }
}
